package com.osg.common.exception;

/**
 * 限流异常
 */
public class RateLimitException extends GatewayException {

    private static final long serialVersionUID = 1L;

    private int limit;
    private int remaining;
    private long resetTime;

    public RateLimitException(String message) {
        super("RATE_LIMIT_EXCEEDED", message);
    }

    public RateLimitException(int limit, int remaining, long resetTime) {
        super("RATE_LIMIT_EXCEEDED", "请求频率超过限制");
        this.limit = limit;
        this.remaining = remaining;
        this.resetTime = resetTime;
    }

    public int getLimit() {
        return limit;
    }

    public int getRemaining() {
        return remaining;
    }

    public long getResetTime() {
        return resetTime;
    }
}
