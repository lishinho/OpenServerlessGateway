package com.osg.ratelimit;

import com.osg.api.spi.RateLimiter;
import com.osg.common.enums.RateLimitAlgorithm;
import com.osg.common.enums.RateLimitDimension;
import com.osg.common.exception.RateLimitException;
import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;

/**
 * 滑动窗口限流器
 * 精确限流，无突发流量
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    private static final String NAME = "SlidingWindowRateLimiter";
    private static final String TYPE = RateLimitAlgorithm.SLIDING_WINDOW.getCode();

    private RedissonClient redissonClient;
    private ApiMetadata.RateLimitConfig config;

    private static final String LUA_SCRIPT = 
        "local key = KEYS[1]\n" +
        "local limit = tonumber(ARGV[1])\n" +
        "local window = tonumber(ARGV[2])\n" +
        "local now = tonumber(ARGV[3])\n" +
        "\n" +
        "redis.call('zremrangebyscore', key, 0, now - window * 1000)\n" +
        "local count = redis.call('zcard', key)\n" +
        "\n" +
        "if count < limit then\n" +
        "    redis.call('zadd', key, now, now .. '-' .. math.random())\n" +
        "    redis.call('expire', key, window)\n" +
        "    return {1, limit - count - 1}\n" +
        "else\n" +
        "    return {0, 0}\n" +
        "end";

    @Override
    public void init(ApiMetadata.RateLimitConfig config) {
        this.config = config;
        log.info("初始化滑动窗口限流器: qps={}", config.getQps());
    }

    @Override
    public boolean tryAcquire(GatewayContext context, ApiMetadata apiMetadata) {
        String key = buildKey(context, apiMetadata);
        long now = System.currentTimeMillis();
        int windowSize = 1;

        try {
            RScript script = redissonClient.getScript();
            Object result = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_SCRIPT,
                RScript.ReturnType.MULTI,
                Collections.singletonList(key),
                config.getQps(), windowSize, now
            );

            if (result instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) result;
                long allowed = ((Number) list.get(0)).longValue();
                long remaining = ((Number) list.get(1)).longValue();

                if (allowed == 1) {
                    log.debug("滑动窗口限流通过: key={}, remaining={}", key, remaining);
                    return true;
                } else {
                    log.warn("滑动窗口限流拒绝: key={}", key);
                    throw new RateLimitException(config.getQps(), (int) remaining, now + windowSize * 1000);
                }
            }
            return false;
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.error("滑动窗口限流异常: key={}, error={}", key, e.getMessage(), e);
            return true;
        }
    }

    private String buildKey(GatewayContext context, ApiMetadata apiMetadata) {
        String dimension = config.getDimension();
        RateLimitDimension dim = RateLimitDimension.fromCode(dimension);
        
        StringBuilder keyBuilder = new StringBuilder("osg:rate:sw:");
        keyBuilder.append(apiMetadata.getApiId()).append(":");
        
        switch (dim) {
            case IP:
                keyBuilder.append("ip:").append(context.getClientIp());
                break;
            case USER:
                keyBuilder.append("user:").append(context.getUserId());
                break;
            case BUSINESS:
                keyBuilder.append("biz:").append(context.getTenantId());
                break;
            case API:
            default:
                keyBuilder.append("api");
                break;
        }
        
        return keyBuilder.toString();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void destroy() {
        log.info("销毁滑动窗口限流器");
    }

    public void setRedissonClient(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }
}
