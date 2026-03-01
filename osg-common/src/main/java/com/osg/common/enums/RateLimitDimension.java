package com.osg.common.enums;

/**
 * 限流维度枚举
 */
public enum RateLimitDimension {

    API("api", "接口级限流"),

    IP("ip", "IP级限流"),

    USER("user", "用户级限流"),

    BUSINESS("business", "业务级限流"),

    COMBO("combo", "组合维度限流");

    private final String code;
    private final String desc;

    RateLimitDimension(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RateLimitDimension fromCode(String code) {
        for (RateLimitDimension dimension : values()) {
            if (dimension.code.equals(code)) {
                return dimension;
            }
        }
        return API;
    }
}
