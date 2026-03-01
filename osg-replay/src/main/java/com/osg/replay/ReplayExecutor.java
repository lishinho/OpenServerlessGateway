package com.osg.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 流量回放执行器
 * 支持离线回放和回放验证
 */
public class ReplayExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReplayExecutor.class);

    private static final String RECORD_KEY_PREFIX = "osg:replay:record:";
    
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;

    public ReplayExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 回放指定记录
     * @param traceId 追踪ID
     * @param executor 执行函数
     * @return 回放结果
     */
    public ReplayResult replay(String traceId, Function<TrafficRecord, Object> executor) {
        TrafficRecord record = getRecord(traceId);
        if (record == null) {
            return ReplayResult.error("录制记录不存在: " + traceId);
        }

        try {
            log.info("开始回放: traceId={}, path={}", traceId, record.getRequestPath());
            
            long startTime = System.currentTimeMillis();
            Object result = executor.apply(record);
            long duration = System.currentTimeMillis() - startTime;
            
            ReplayResult replayResult = new ReplayResult();
            replayResult.setTraceId(traceId);
            replayResult.setOriginalDuration(record.getDuration());
            replayResult.setReplayDuration(duration);
            replayResult.setSuccess(true);
            replayResult.setResult(result);
            
            log.info("回放完成: traceId={}, duration={}ms", traceId, duration);
            return replayResult;
        } catch (Exception e) {
            log.error("回放失败: traceId={}, error={}", traceId, e.getMessage(), e);
            return ReplayResult.error("回放失败: " + e.getMessage());
        }
    }

    /**
     * 批量回放
     * @param traceIds 追踪ID列表
     * @param executor 执行函数
     * @return 回放结果列表
     */
    public List<ReplayResult> batchReplay(List<String> traceIds, Function<TrafficRecord, Object> executor) {
        List<ReplayResult> results = new ArrayList<>();
        for (String traceId : traceIds) {
            results.add(replay(traceId, executor));
        }
        return results;
    }

    /**
     * 获取录制记录
     */
    private TrafficRecord getRecord(String traceId) {
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

    /**
     * 获取所有录制记录ID
     */
    public List<String> getAllRecordIds() {
        Set<String> keys = redisTemplate.keys(RECORD_KEY_PREFIX + "*");
        if (keys == null) {
            return new ArrayList<>();
        }
        List<String> traceIds = new ArrayList<>();
        for (String key : keys) {
            traceIds.add(key.substring(RECORD_KEY_PREFIX.length()));
        }
        return traceIds;
    }
}
