package com.osg.common.model;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * API元数据配置
 */
@Data
public class ApiMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private String apiId;
    private String httpPath;
    private String httpMethod;
    private String protocol;
    private String serviceName;
    private String serviceGroup;
    private String serviceVersion;
    private String methodName;
    private int timeout;
    private boolean authRequired;
    private String[] permissions;
    private RateLimitConfig rateLimitConfig;
    private RetryConfig retryConfig;
    private DegradeConfig degradeConfig;
    private GrayConfig grayConfig;
    private Map<String, ParamMapping> paramMappings;

    @Data
    public static class RateLimitConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled;
        private int qps;
        private String dimension;
        private int burstCapacity;
    }

    @Data
    public static class RetryConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled;
        private int maxRetries;
        private int retryInterval;
        private String strategy;
        private String[] retryOnErrors;
    }

    @Data
    public static class DegradeConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled;
        private String strategy;
        private String fallbackService;
        private Object defaultValue;
        private int errorThreshold;
        private int timeWindow;
    }

    @Data
    public static class GrayConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean enabled;
        private String strategy;
        private int weight;
        private String[] userWhitelist;
        private String[] ipWhitelist;
        private Map<String, String> headerRules;
    }

    @Data
    public static class ParamMapping implements Serializable {
        private static final long serialVersionUID = 1L;
        private String httpParam;
        private String rpcParam;
        private String paramType;
        private String source;
    }
}
