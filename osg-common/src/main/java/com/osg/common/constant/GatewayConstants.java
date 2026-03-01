package com.osg.common.constant;

/**
 * 网关常量定义
 */
public final class GatewayConstants {

    private GatewayConstants() {
    }

    public static final String GATEWAY_NAME = "OpenServerlessGateway";
    public static final String GATEWAY_VERSION = "1.0.0";

    public static final String CONTEXT_KEY_USER_ID = "X-User-Id";
    public static final String CONTEXT_KEY_USER_NAME = "X-User-Name";
    public static final String CONTEXT_KEY_USER_ROLES = "X-User-Roles";
    public static final String CONTEXT_KEY_USER_PERMISSIONS = "X-User-Permissions";
    public static final String CONTEXT_KEY_TENANT_ID = "X-Tenant-Id";
    public static final String CONTEXT_KEY_TRACE_ID = "X-Trace-Id";
    public static final String CONTEXT_KEY_AUTH_TIME = "X-Auth-Time";
    public static final String CONTEXT_KEY_AUTH_SIGNATURE = "X-Auth-Signature";

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_BEARER_PREFIX = "Bearer ";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";

    public static final String REDIS_KEY_PREFIX = "osg:";
    public static final String REDIS_KEY_RATE_LIMIT = REDIS_KEY_PREFIX + "rate:";
    public static final String REDIS_KEY_AUTH = REDIS_KEY_PREFIX + "auth:";
    public static final String REDIS_KEY_GRAY = REDIS_KEY_PREFIX + "gray:";
    public static final String REDIS_KEY_SESSION = REDIS_KEY_PREFIX + "session:";
    public static final String REDIS_KEY_REPLAY = REDIS_KEY_PREFIX + "replay:";
    public static final String REDIS_KEY_ANTIBRUSH = REDIS_KEY_PREFIX + "antibrush:";

    public static final int DEFAULT_RATE_LIMIT_QPS = 1000;
    public static final int DEFAULT_TIMEOUT_MS = 3000;
    public static final int DEFAULT_RETRY_TIMES = 3;
    public static final int DEFAULT_RETRY_INTERVAL_MS = 100;

    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final String JSON_CONTENT_TYPE = "application/json";
}
