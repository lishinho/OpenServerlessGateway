package com.osg.common.enums;

/**
 * 认证类型枚举
 */
public enum AuthType {

    JWT("jwt", "JWT认证"),

    SESSION("session", "Session认证"),

    API_KEY("api_key", "API Key认证"),

    OAUTH2("oauth2", "OAuth2认证"),

    NONE("none", "无需认证");

    private final String code;
    private final String desc;

    AuthType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static AuthType fromCode(String code) {
        for (AuthType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return NONE;
    }
}
