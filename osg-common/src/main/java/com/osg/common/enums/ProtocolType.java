package com.osg.common.enums;

/**
 * 协议类型枚举
 */
public enum ProtocolType {

    HTTP("http", "HTTP协议"),

    HTTPS("https", "HTTPS协议"),

    DUBBO("dubbo", "Dubbo协议"),

    GRPC("grpc", "gRPC协议"),

    SOFA("sofa", "SOFA协议"),

    THRIFT("thrift", "Thrift协议"),

    WEBSOCKET("websocket", "WebSocket协议");

    private final String code;
    private final String desc;

    ProtocolType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ProtocolType fromCode(String code) {
        for (ProtocolType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return HTTP;
    }
}
