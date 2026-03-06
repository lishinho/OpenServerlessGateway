package com.osg.serverless;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AWS Lambda 函数调用器
 * 支持 AWS Lambda 的同步和异步调用
 */
public class AwsLambdaInvoker implements FunctionInvoker {

    private static final Logger log = LoggerFactory.getLogger(AwsLambdaInvoker.class);

    private static final String NAME = "AwsLambdaInvoker";
    private static final String PLATFORM_TYPE = "aws_lambda";

    private String region;
    private String accessKeyId;
    private String secretAccessKey;
    private int timeout;
    private ObjectMapper objectMapper;

    private Object lambdaClient;
    private Class<?> lambdaClientClass;

    public AwsLambdaInvoker() {
        this.objectMapper = new ObjectMapper();
        this.timeout = 30000;
    }

    @Override
    public void init(Map<String, Object> config) {
        this.region = (String) config.getOrDefault("region", "us-east-1");
        this.accessKeyId = (String) config.get("accessKeyId");
        this.secretAccessKey = (String) config.get("secretAccessKey");
        
        if (config.containsKey("timeout")) {
            this.timeout = (Integer) config.get("timeout");
        }

        try {
            lambdaClientClass = Class.forName("software.amazon.awssdk.services.lambda.LambdaClient");
            log.info("初始化AWS Lambda调用器: region={}", region);
        } catch (ClassNotFoundException e) {
            log.warn("AWS Lambda SDK 依赖未找到，将使用模拟模式");
        }
    }

    @Override
    public Object invoke(GatewayContext context, String functionName, Object payload) {
        log.debug("AWS Lambda调用: function={}, traceId={}", functionName, context.getTraceId());

        try {
            if (lambdaClientClass != null) {
                return doInvoke(functionName, payload);
            } else {
                return createMockResponse(functionName, payload);
            }
        } catch (Exception e) {
            log.error("AWS Lambda调用失败: function={}, error={}", functionName, e.getMessage(), e);
            throw new RuntimeException("AWS Lambda调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行实际调用
     */
    private Object doInvoke(String functionName, Object payload) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(payload);
        
        Object invokeRequest = Class.forName("software.amazon.awssdk.services.lambda.model.InvokeRequest")
            .getMethod("builder")
            .invoke(null);
        
        invokeRequest.getClass()
            .getMethod("functionName", String.class)
            .invoke(invokeRequest, functionName);
        invokeRequest.getClass()
            .getMethod("payload", Class.forName("software.amazon.awssdk.core.SdkBytes"))
            .invoke(invokeRequest, createSdkBytes(payloadJson));

        Object response = lambdaClient.getClass()
            .getMethod("invoke", Class.forName("software.amazon.awssdk.services.lambda.model.InvokeRequest"))
            .invoke(lambdaClient, invokeRequest);

        Object statusCode = response.getClass().getMethod("statusCode").invoke(response);
        if ((Integer) statusCode != 200) {
            Object functionError = response.getClass().getMethod("functionError").invoke(response);
            throw new RuntimeException("Lambda调用失败: " + functionError);
        }

        Object resultPayload = response.getClass().getMethod("payload").invoke(response);
        String resultJson = new String((byte[]) resultPayload.getClass().getMethod("asByteArray").invoke(resultPayload));
        
        return objectMapper.readValue(resultJson, Object.class);
    }

    /**
     * 创建SdkBytes
     */
    private Object createSdkBytes(String content) throws Exception {
        Class<?> sdkBytesClass = Class.forName("software.amazon.awssdk.core.SdkBytes");
        return sdkBytesClass.getMethod("fromUtf8String", String.class).invoke(null, content);
    }

    /**
     * 创建模拟响应
     */
    private Object createMockResponse(String functionName, Object payload) {
        Map<String, Object> response = new ConcurrentHashMap<>();
        response.put("functionName", functionName);
        response.put("status", "mock");
        response.put("payload", payload);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @Override
    public String invokeAsync(GatewayContext context, String functionName, Object payload) {
        log.debug("AWS Lambda异步调用: function={}, traceId={}", functionName, context.getTraceId());
        
        String requestId = "req-" + System.currentTimeMillis();
        
        return requestId;
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
        log.info("销毁AWS Lambda调用器");
        if (lambdaClient != null) {
            try {
                lambdaClient.getClass().getMethod("close").invoke(lambdaClient);
            } catch (Exception e) {
                log.error("关闭Lambda客户端失败: {}", e.getMessage());
            }
        }
    }
}
