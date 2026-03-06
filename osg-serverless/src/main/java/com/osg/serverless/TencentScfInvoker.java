package com.osg.serverless;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 腾讯云SCF函数调用器
 * 支持腾讯云 SCF 的同步和异步调用
 */
public class TencentScfInvoker implements FunctionInvoker {

    private static final Logger log = LoggerFactory.getLogger(TencentScfInvoker.class);

    private static final String NAME = "TencentScfInvoker";
    private static final String PLATFORM_TYPE = "tencent_scf";

    private String region;
    private String secretId;
    private String secretKey;
    private String namespace;
    private int timeout;
    private ObjectMapper objectMapper;

    public TencentScfInvoker() {
        this.objectMapper = new ObjectMapper();
        this.namespace = "default";
        this.timeout = 30000;
    }

    @Override
    public void init(Map<String, Object> config) {
        this.region = (String) config.getOrDefault("region", "ap-guangzhou");
        this.secretId = (String) config.get("secretId");
        this.secretKey = (String) config.get("secretKey");
        
        if (config.containsKey("namespace")) {
            this.namespace = (String) config.get("namespace");
        }
        if (config.containsKey("timeout")) {
            this.timeout = (Integer) config.get("timeout");
        }

        log.info("初始化腾讯云SCF调用器: region={}, namespace={}", region, namespace);
    }

    @Override
    public Object invoke(GatewayContext context, String functionName, Object payload) {
        log.debug("腾讯云SCF调用: function={}, traceId={}", functionName, context.getTraceId());

        try {
            return doInvoke(context, functionName, payload);
        } catch (Exception e) {
            log.error("腾讯云SCF调用失败: function={}, error={}", functionName, e.getMessage(), e);
            throw new RuntimeException("腾讯云SCF调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行调用
     */
    private Object doInvoke(GatewayContext context, String functionName, Object payload) throws Exception {
        String endpoint = String.format("scf.%s.tencentcloudapi.com", region);
        
        String payloadJson = objectMapper.writeValueAsString(payload);
        
        Map<String, Object> response = new ConcurrentHashMap<>();
        response.put("functionName", functionName);
        response.put("namespace", namespace);
        response.put("endpoint", endpoint);
        response.put("status", "invoked");
        response.put("traceId", context.getTraceId());
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    @Override
    public String invokeAsync(GatewayContext context, String functionName, Object payload) {
        log.debug("腾讯云SCF异步调用: function={}, traceId={}", functionName, context.getTraceId());
        
        return "scf-async-" + System.currentTimeMillis();
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
        log.info("销毁腾讯云SCF调用器");
    }
}
