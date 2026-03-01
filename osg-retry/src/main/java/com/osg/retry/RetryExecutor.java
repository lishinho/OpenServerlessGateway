package com.osg.retry;

import com.osg.common.enums.RetryStrategyType;
import com.osg.common.model.ApiMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 智能重试执行器
 * 支持固定间隔、指数退避、随机延迟策略
 */
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private int maxRetries;
    private int retryInterval;
    private RetryStrategyType strategy;
    private Set<String> retryOnErrors;
    private Set<String> retryOnCodes;

    public RetryExecutor(ApiMetadata.RetryConfig config) {
        this.maxRetries = config.getMaxRetries() > 0 ? config.getMaxRetries() : 3;
        this.retryInterval = config.getRetryInterval() > 0 ? config.getRetryInterval() : 100;
        this.strategy = RetryStrategyType.fromCode(config.getStrategy());
        this.retryOnErrors = config.getRetryOnErrors() != null 
            ? new HashSet<>(Arrays.asList(config.getRetryOnErrors())) 
            : new HashSet<>();
        this.retryOnCodes = new HashSet<>();
    }

    /**
     * 执行带重试的操作
     * @param supplier 操作
     * @param context 上下文信息
     * @return 操作结果
     */
    public <T> T execute(Supplier<T> supplier, String context) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                T result = supplier.get();
                if (attempt > 0) {
                    log.info("重试成功: context={}, attempt={}", context, attempt);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (!shouldRetry(e) || attempt > maxRetries) {
                    log.error("重试终止: context={}, attempt={}, error={}", context, attempt, e.getMessage());
                    break;
                }

                long delay = calculateDelay(attempt);
                log.warn("准备重试: context={}, attempt={}, delay={}ms, error={}", 
                    context, attempt, delay, e.getMessage());

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastException != null) {
            if (lastException instanceof RuntimeException) {
                throw (RuntimeException) lastException;
            }
            throw new RuntimeException("重试失败: " + lastException.getMessage(), lastException);
        }
        return null;
    }

    /**
     * 判断是否应该重试
     */
    private boolean shouldRetry(Exception e) {
        if (retryOnErrors.isEmpty()) {
            return true;
        }

        String errorType = e.getClass().getSimpleName();
        return retryOnErrors.contains(errorType);
    }

    /**
     * 计算重试延迟
     */
    private long calculateDelay(int attempt) {
        switch (strategy) {
            case EXPONENTIAL:
                return retryInterval * (long) Math.pow(2, attempt - 1);
            case RANDOM:
                return retryInterval + ThreadLocalRandom.current().nextLong(0, retryInterval);
            case FIXED:
            default:
                return retryInterval;
        }
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getRetryInterval() {
        return retryInterval;
    }

    public RetryStrategyType getStrategy() {
        return strategy;
    }
}
