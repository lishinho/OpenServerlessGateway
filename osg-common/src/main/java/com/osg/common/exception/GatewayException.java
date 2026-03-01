package com.osg.common.exception;

/**
 * 网关基础异常
 */
public class GatewayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private String errorCode;
    private String errorMessage;

    public GatewayException(String errorMessage) {
        super(errorMessage);
        this.errorMessage = errorMessage;
    }

    public GatewayException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public GatewayException(String errorCode, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
