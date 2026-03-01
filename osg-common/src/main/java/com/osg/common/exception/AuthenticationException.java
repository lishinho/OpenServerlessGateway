package com.osg.common.exception;

/**
 * 认证异常
 */
public class AuthenticationException extends GatewayException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super("AUTHENTICATION_FAILED", message);
    }

    public AuthenticationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
