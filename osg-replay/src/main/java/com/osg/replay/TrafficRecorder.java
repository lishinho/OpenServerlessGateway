package com.osg.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 流量录制器
 * 支持请求的全量/增量录制
 */
public class TrafficRecorder {

    private static final Logger log = LoggerFactory.getLogger(TrafficRecorder.class);

    private static final String RECORD_KEY_PREFIX = "osg:replay:record:";
    
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private boolean enabled;
    private double sampleRate;
    private int expireDays;

    public TrafficRecorder(StringRedisTemplate redisTemplate, double sampleRate, int expireDays) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.sampleRate = sampleRate;
        this.expireDays = expireDays;
        this.enabled = true;
    }

    /**
     * 录制请求
     * @param context 网关上下文
     * @param requestBody 请求体
     * @param responseBody 响应体
     * @param duration 耗时
     * @param success 是否成功
     */
    public void record(GatewayContext context, String requestBody, 
                       String responseBody, long duration, boolean success) {
        if (!enabled || !shouldSample()) {
            return;
        }

        try {
            TrafficRecord record = new TrafficRecord();
            record.setTraceId(context.getTraceId());
            record.setRequestPath(context.getRequestPath());
            record.setHttpMethod(context.getHttpMethod());
            record.setClientIp(context.getClientIp());
            record.setUserId(context.getUserId());
            record.setRequestBody(requestBody);
            record.setResponseBody(responseBody);
            record.setDuration(duration);
            record.setSuccess(success);
            record.setTimestamp(System.currentTimeMillis());
            
            Map<String, String> headers = new HashMap<>();
            headers.put("X-User-Id", context.getUserId());
            headers.put("X-Tenant-Id", context.getTenantId());
            record.setHeaders(headers);

            String recordJson = objectMapper.writeValueAsString(record);
            String key = RECORD_KEY_PREFIX + context.getTraceId();
            
            redisTemplate.opsForValue().set(key, recordJson, expireDays, TimeUnit.DAYS);
            
            log.debug("流量录制成功: traceId={}", context.getTraceId());
        } catch (Exception e) {
            log.error("流量录制失败: traceId={}, error={}", context.getTraceId(), e.getMessage());
        }
    }

    /**
     * 判断是否采样
     */
    private boolean shouldSample() {
        if (sampleRate >= 1.0) {
            return true;
        }
        if (sampleRate <= 0.0) {
            return false;
        }
        return Math.random() < sampleRate;
    }

    /**
     * 获取录制记录
     * @param traceId 追踪ID
     * @return 录制记录
     */
    public TrafficRecord getRecord(String traceId) {
        try {
            String key = RECORD_KEY_PREFIX + traceId;
            String recordJson = redisTemplate.opsForValue().get(key);
            
            if (recordJson != null) {
                return objectMapper.readValue(recordJson, TrafficRecord.class);
            }
        } catch (Exception e) {
            log.error("获取录制记录失败: traceId={}, error={}", traceId, e.getMessage());
        }
        return null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }
}
