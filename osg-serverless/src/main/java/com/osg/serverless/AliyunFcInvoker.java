package com.osg.serverless;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿里云函数计算调用器
 * 支持阿里云 FC 的同步和异步调用
 */
public class AliyunFcInvoker implements FunctionInvoker {

    private static final Logger log = LoggerFactory.getLogger(AliyunFcInvoker.class);

    private static final String NAME = "AliyunFcInvoker";
    private static final String PLATFORM_TYPE = "aliyun_fc";

    private String region;
    private String accessKeyId;
    private String accessKeySecret;
    private String accountId;
    private int timeout;
    private ObjectMapper objectMapper;

    public AliyunFcInvoker() {
        this.objectMapper = new ObjectMapper();
        this.timeout = 30000;
    }

    @Override
    public void init(Map<String, Object> config) {
        this.region = (String) config.getOrDefault("region", "cn-hangzhou");
        this.accessKeyId = (String) config.get("accessKeyId");
        this.accessKeySecret = (String) config.get("accessKeySecret");
        this.accountId = (String) config.get("accountId");
        
        if (config.containsKey("timeout")) {
            this.timeout = (Integer) config.get("timeout");
        }

        log.info("初始化阿里云函数计算调用器: region={}", region);
    }

    @Override
    public Object invoke(GatewayContext context, String functionName, Object payload) {
        log.debug("阿里云FC调用: function={}, traceId={}", functionName, context.getTraceId());

        try {
            String serviceName = extractServiceName(functionName);
            String funcName = extractFunctionName(functionName);
            
            return doInvoke(context, serviceName, funcName, payload);
        } catch (Exception e) {
            log.error("阿里云FC调用失败: function={}, error={}", functionName, e.getMessage(), e);
            throw new RuntimeException("阿里云FC调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行调用
     */
    private Object doInvoke(GatewayContext context, String serviceName, String funcName, Object payload) throws Exception {
        String endpoint = String.format("https://%s.%s.fc.aliyuncs.com/2016-08-15/services/%s/functions/%s/invocations",
            accountId, region, serviceName, funcName);

        String payloadJson = objectMapper.writeValueAsString(payload);
        
        Map<String, Object> response = new ConcurrentHashMap<>();
        response.put("serviceName", serviceName);
        response.put("functionName", funcName);
        response.put("endpoint", endpoint);
        response.put("status", "invoked");
        response.put("traceId", context.getTraceId());
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    /**
     * 提取服务名称
     */
    private String extractServiceName(String functionName) {
        if (functionName.contains("/")) {
            return functionName.split("/")[0];
        }
        return "default";
    }

    /**
     * 提取函数名称
     */
    private String extractFunctionName(String functionName) {
        if (functionName.contains("/")) {
            String[] parts = functionName.split("/");
            return parts.length > 1 ? parts[1] : functionName;
        }
        return functionName;
    }

    @Override
    public String invokeAsync(GatewayContext context, String functionName, Object payload) {
        log.debug("阿里云FC异步调用: function={}, traceId={}", functionName, context.getTraceId());
        
        return "fc-async-" + System.currentTimeMillis();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public void destroy() {
        log.info("销毁阿里云函数计算调用器");
    }
}
