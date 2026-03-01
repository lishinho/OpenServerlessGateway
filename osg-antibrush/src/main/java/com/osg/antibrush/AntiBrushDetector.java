package com.osg.antibrush;

import com.osg.common.model.GatewayContext;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;

/**
 * 接口防刷检测器
 * 支持IP/用户级防刷，动态阈值配置
 */
public class AntiBrushDetector {

    private static final Logger log = LoggerFactory.getLogger(AntiBrushDetector.class);

    private static final String IP_KEY_PREFIX = "osg:antibrush:ip:";
    private static final String USER_KEY_PREFIX = "osg:antibrush:user:";

    private RedissonClient redissonClient;
    private int ipThreshold;
    private int userThreshold;
    private int windowSeconds;
    private boolean enabled;

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
        "    return {1, count + 1}\n" +
        "else\n" +
        "    return {0, count}\n" +
        "end";

    public AntiBrushDetector(RedissonClient redissonClient, int ipThreshold, int userThreshold, int windowSeconds) {
        this.redissonClient = redissonClient;
        this.ipThreshold = ipThreshold;
        this.userThreshold = userThreshold;
        this.windowSeconds = windowSeconds;
        this.enabled = true;
    }

    /**
     * 检测是否触发防刷
     * @param context 网关上下文
     * @param apiPath API路径
     * @return 是否允许访问
     */
    public boolean check(GatewayContext context, String apiPath) {
        if (!enabled) {
            return true;
        }

        boolean ipCheck = checkByIp(context.getClientIp(), apiPath);
        boolean userCheck = checkByUser(context.getUserId(), apiPath);

        if (!ipCheck) {
            log.warn("IP防刷触发: ip={}, path={}", context.getClientIp(), apiPath);
            return false;
        }

        if (!userCheck) {
            log.warn("用户防刷触发: userId={}, path={}", context.getUserId(), apiPath);
            return false;
        }

        return true;
    }

    /**
     * IP级别检测
     */
    private boolean checkByIp(String clientIp, String apiPath) {
        if (clientIp == null || clientIp.isEmpty()) {
            return true;
        }

        String key = IP_KEY_PREFIX + clientIp + ":" + apiPath;
        return doCheck(key, ipThreshold);
    }

    /**
     * 用户级别检测
     */
    private boolean checkByUser(String userId, String apiPath) {
        if (userId == null || userId.isEmpty()) {
            return true;
        }

        String key = USER_KEY_PREFIX + userId + ":" + apiPath;
        return doCheck(key, userThreshold);
    }

    /**
     * 执行检测
     */
    private boolean doCheck(String key, int threshold) {
        try {
            RScript script = redissonClient.getScript();
            Object result = script.eval(
                RScript.Mode.READ_WRITE,
                LUA_SCRIPT,
                RScript.ReturnType.MULTI,
                Collections.singletonList(key),
                threshold, windowSeconds, System.currentTimeMillis()
            );

            if (result instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) result;
                long allowed = ((Number) list.get(0)).longValue();
                return allowed == 1;
            }
            return true;
        } catch (Exception e) {
            log.error("防刷检测异常: key={}, error={}", key, e.getMessage());
            return true;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setIpThreshold(int ipThreshold) {
        this.ipThreshold = ipThreshold;
    }

    public void setUserThreshold(int userThreshold) {
        this.userThreshold = userThreshold;
    }
}
