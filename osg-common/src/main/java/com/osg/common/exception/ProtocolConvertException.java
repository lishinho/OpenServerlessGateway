package com.osg.common.exception;

/**
 * 协议转换异常
 */
public class ProtocolConvertException extends GatewayException {

    private static final long serialVersionUID = 1L;

    public ProtocolConvertException(String message) {
        super("PROTOCOL_CONVERT_ERROR", message);
    }

    public ProtocolConvertException(String message, Throwable cause) {
        super("PROTOCOL_CONVERT_ERROR", message, cause);
    }
}
