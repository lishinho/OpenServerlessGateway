package com.osg.common.enums;

/**
 * 重试策略类型枚举
 */
public enum RetryStrategyType {

    FIXED("fixed", "固定间隔"),

    EXPONENTIAL("exponential", "指数退避"),

    RANDOM("random", "随机延迟");

    private final String code;
    private final String desc;

    RetryStrategyType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RetryStrategyType fromCode(String code) {
        for (RetryStrategyType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return FIXED;
    }
}
