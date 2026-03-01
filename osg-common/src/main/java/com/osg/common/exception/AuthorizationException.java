package com.osg.common.exception;

/**
 * 权限异常
 */
public class AuthorizationException extends GatewayException {

    private static final long serialVersionUID = 1L;

    public AuthorizationException(String message) {
        super("AUTHORIZATION_DENIED", message);
    }

    public AuthorizationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
