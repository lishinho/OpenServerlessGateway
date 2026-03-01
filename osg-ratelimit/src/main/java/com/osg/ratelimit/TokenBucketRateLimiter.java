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
import java.util.Arrays;
import java.util.Collections;

/**
 * 令牌桶限流器
 * 支持突发流量，平滑限流
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private static final String NAME = "TokenBucketRateLimiter";
    private static final String TYPE = RateLimitAlgorithm.TOKEN_BUCKET.getCode();

    private RedissonClient redissonClient;
    private ApiMetadata.RateLimitConfig config;

    private static final String LUA_SCRIPT = 
        "local key = KEYS[1]\n" +
        "local capacity = tonumber(ARGV[1])\n" +
        "local rate = tonumber(ARGV[2])\n" +
        "local requested = tonumber(ARGV[3])\n" +
        "local now = tonumber(ARGV[4])\n" +
        "\n" +
        "local last_refill = redis.call('hget', key, 'last_refill')\n" +
        "local tokens = redis.call('hget', key, 'tokens')\n" +
        "\n" +
        "if last_refill == false then\n" +
        "    last_refill = now\n" +
        "    tokens = capacity\n" +
        "end\n" +
        "\n" +
        "local elapsed = now - last_refill\n" +
        "local refill_tokens = math.floor(elapsed * rate)\n" +
        "tokens = math.min(capacity, tokens + refill_tokens)\n" +
        "\n" +
        "local allowed = 0\n" +
        "if tokens >= requested then\n" +
        "    tokens = tokens - requested\n" +
        "    allowed = 1\n" +
        "end\n" +
        "\n" +
        "redis.call('hset', key, 'tokens', tokens)\n" +
        "redis.call('hset', key, 'last_refill', now)\n" +
        "redis.call('expire', key, 3600)\n" +
        "\n" +
        "return {allowed, tokens}";

    @Override
    public void init(ApiMetadata.RateLimitConfig config) {
        this.config = config;
        log.info("初始化令牌桶限流器: qps={}, burstCapacity={}", config.getQps(), config.getBurstCapacity());
    }

    @Override
    public boolean tryAcquire(GatewayContext context, ApiMetadata apiMetadata) {
        String key = buildKey(context, apiMetadata);
        int capacity = config.getBurstCapacity() > 0 ? config.getBurstCapacity() : config.getQps();
        double rate = config.getQps() / 1000.0;
        long now = System.currentTimeMillis();

        try {
            RScript script = redissonClient.getScript();
            Object result = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_SCRIPT,
                RScript.ReturnType.MULTI,
                Collections.singletonList(key),
                capacity, rate, 1, now
            );

            if (result instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) result;
                long allowed = ((Number) list.get(0)).longValue();
                long remaining = ((Number) list.get(1)).longValue();

                if (allowed == 1) {
                    log.debug("令牌桶限流通过: key={}, remaining={}", key, remaining);
                    return true;
                } else {
                    log.warn("令牌桶限流拒绝: key={}", key);
                    throw new RateLimitException(config.getQps(), (int) remaining, now + 1000);
                }
            }
            return false;
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            log.error("令牌桶限流异常: key={}, error={}", key, e.getMessage(), e);
            return true;
        }
    }

    /**
     * 构建限流Key
     * @param context 网关上下文
     * @param apiMetadata API元数据
     * @return 限流Key
     */
    private String buildKey(GatewayContext context, ApiMetadata apiMetadata) {
        String dimension = config.getDimension();
        RateLimitDimension dim = RateLimitDimension.fromCode(dimension);
        
        StringBuilder keyBuilder = new StringBuilder("osg:rate:");
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
            case COMBO:
                keyBuilder.append("combo:").append(context.getClientIp())
                    .append(":").append(context.getUserId());
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
        log.info("销毁令牌桶限流器");
    }

    public void setRedissonClient(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }
}
