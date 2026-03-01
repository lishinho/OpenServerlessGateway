package com.osg.degrade;

import com.osg.common.enums.DegradeStrategyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 降级管理器
 * 支持接口级/业务级自动降级
 */
public class DegradeManager {

    private static final Logger log = LoggerFactory.getLogger(DegradeManager.class);

    private static final String DEGRADE_KEY_PREFIX = "osg:degrade:";
    
    private StringRedisTemplate redisTemplate;
    private Map<String, DegradeState> degradeStates = new ConcurrentHashMap<>();
    private int errorThreshold;
    private int timeWindowSeconds;

    public DegradeManager(StringRedisTemplate redisTemplate, int errorThreshold, int timeWindowSeconds) {
        this.redisTemplate = redisTemplate;
        this.errorThreshold = errorThreshold;
        this.timeWindowSeconds = timeWindowSeconds;
    }

    /**
     * 记录错误
     * @param apiId API标识
     */
    public void recordError(String apiId) {
        DegradeState state = degradeStates.computeIfAbsent(apiId, k -> new DegradeState());
        int errorCount = state.incrementError();
        
        if (errorCount >= errorThreshold && !state.isDegrading()) {
            triggerDegrade(apiId);
        }
    }

    /**
     * 记录成功
     * @param apiId API标识
     */
    public void recordSuccess(String apiId) {
        DegradeState state = degradeStates.get(apiId);
        if (state != null) {
            state.resetError();
            
            if (state.isDegrading()) {
                recoverDegrade(apiId);
            }
        }
    }

    /**
     * 判断是否降级
     * @param apiId API标识
     * @return 是否降级
     */
    public boolean isDegrading(String apiId) {
        DegradeState state = degradeStates.get(apiId);
        return state != null && state.isDegrading();
    }

    /**
     * 触发降级
     * @param apiId API标识
     */
    public void triggerDegrade(String apiId) {
        DegradeState state = degradeStates.computeIfAbsent(apiId, k -> new DegradeState());
        state.setDegrading(true);
        state.setDegradeStartTime(System.currentTimeMillis());
        
        String key = DEGRADE_KEY_PREFIX + apiId;
        redisTemplate.opsForValue().set(key, "1", timeWindowSeconds, java.util.concurrent.TimeUnit.SECONDS);
        
        log.warn("触发降级: apiId={}", apiId);
    }

    /**
     * 恢复降级
     * @param apiId API标识
     */
    public void recoverDegrade(String apiId) {
        DegradeState state = degradeStates.get(apiId);
        if (state != null) {
            state.setDegrading(false);
            state.resetError();
        }
        
        String key = DEGRADE_KEY_PREFIX + apiId;
        redisTemplate.delete(key);
        
        log.info("恢复降级: apiId={}", apiId);
    }

    /**
     * 获取降级响应
     * @param apiId API标识
     * @param strategy 降级策略
     * @param defaultValue 默认值
     * @return 降级响应
     */
    public Object getDegradeResponse(String apiId, DegradeStrategyType strategy, Object defaultValue) {
        log.debug("返回降级响应: apiId={}, strategy={}", apiId, strategy);
        
        switch (strategy) {
            case DEFAULT_VALUE:
                return defaultValue;
            case ERROR_PAGE:
                return createErrorResponse(apiId);
            case FALLBACK_SERVICE:
            default:
                return defaultValue;
        }
    }

    /**
     * 创建错误响应
     */
    private Object createErrorResponse(String apiId) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("code", "DEGRADED");
        response.put("message", "服务暂时不可用，请稍后重试");
        response.put("apiId", apiId);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * 降级状态
     */
    private static class DegradeState {
        private AtomicInteger errorCount = new AtomicInteger(0);
        private volatile boolean degrading = false;
        private AtomicLong degradeStartTime = new AtomicLong(0);

        public int incrementError() {
            return errorCount.incrementAndGet();
        }

        public void resetError() {
            errorCount.set(0);
        }

        public boolean isDegrading() {
            return degrading;
        }

        public void setDegrading(boolean degrading) {
            this.degrading = degrading;
        }

        public void setDegradeStartTime(long time) {
            degradeStartTime.set(time);
        }

        public long getDegradeStartTime() {
            return degradeStartTime.get();
        }
    }
}
