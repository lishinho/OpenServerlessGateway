package com.osg.common.enums;

/**
 * 降级策略类型枚举
 */
public enum DegradeStrategyType {

    DEFAULT_VALUE("default_value", "返回默认值"),

    FALLBACK_SERVICE("fallback_service", "转发备用服务"),

    ERROR_PAGE("error_page", "返回错误页");

    private final String code;
    private final String desc;

    DegradeStrategyType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static DegradeStrategyType fromCode(String code) {
        for (DegradeStrategyType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return DEFAULT_VALUE;
    }
}
