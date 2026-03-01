package com.osg.common.enums;

/**
 * 限流算法类型枚举
 */
public enum RateLimitAlgorithm {

    TOKEN_BUCKET("token_bucket", "令牌桶算法"),

    LEAKY_BUCKET("leaky_bucket", "漏桶算法"),

    SLIDING_WINDOW("sliding_window", "滑动窗口算法"),

    CONCURRENT("concurrent", "并发限流");

    private final String code;
    private final String desc;

    RateLimitAlgorithm(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RateLimitAlgorithm fromCode(String code) {
        for (RateLimitAlgorithm algorithm : values()) {
            if (algorithm.code.equals(code)) {
                return algorithm;
            }
        }
        return TOKEN_BUCKET;
    }
}
