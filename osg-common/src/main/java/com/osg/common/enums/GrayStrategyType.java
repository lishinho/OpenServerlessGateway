package com.osg.common.enums;

/**
 * 灰度策略类型枚举
 */
public enum GrayStrategyType {

    USER_WHITELIST("user_whitelist", "用户白名单"),

    IP_WHITELIST("ip_whitelist", "IP白名单"),

    HEADER("header", "Header路由"),

    WEIGHT("weight", "权重分流"),

    BUSINESS_ATTR("business_attr", "业务属性");

    private final String code;
    private final String desc;

    GrayStrategyType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static GrayStrategyType fromCode(String code) {
        for (GrayStrategyType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return WEIGHT;
    }
}
