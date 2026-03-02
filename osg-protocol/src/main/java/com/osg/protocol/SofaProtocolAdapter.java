package com.osg.protocol;

import com.osg.api.spi.ProtocolAdapter;
import com.osg.common.enums.ProtocolType;
import com.osg.common.exception.ProtocolConvertException;
import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SOFAStack协议适配器
 * 支持 SOFA RPC 泛化调用
 */
public class SofaProtocolAdapter implements ProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(SofaProtocolAdapter.class);

    private static final String NAME = "SofaProtocolAdapter";
    private static final String TYPE = ProtocolType.SOFA.getCode();

    private String registryAddress;
    private int defaultTimeout;
    private final Map<String, Object> serviceCache = new ConcurrentHashMap<>();

    private Object sofaRuntime;
    private Class<?> referenceBuilderClass;

    @Override
    public void init(Map<String, Object> config) {
        this.registryAddress = (String) config.get("registryAddress");
        this.defaultTimeout = config.containsKey("defaultTimeout") 
            ? (Integer) config.get("defaultTimeout") : 3000;
        
        try {
            Class<?> sofaRuntimeClass = Class.forName("com.alipay.sofa.runtime.api.annotation.SofaReference");
            referenceBuilderClass = Class.forName("com.alipay.sofa.rpc.api.ReferenceBuilder");
            log.info("初始化SOFAStack协议适配器: registry={}", registryAddress);
        } catch (ClassNotFoundException e) {
            log.warn("SOFA RPC 依赖未找到，SOFAStack适配器将使用模拟模式");
        }
    }

    @Override
    public Object invoke(GatewayContext context, ApiMetadata apiMetadata, Map<String, Object> params) {
        String serviceName = apiMetadata.getServiceName();
        String methodName = apiMetadata.getMethodName();
        String version = apiMetadata.getServiceVersion();
        String group = apiMetadata.getServiceGroup();
        int timeout = apiMetadata.getTimeout() > 0 ? apiMetadata.getTimeout() : defaultTimeout;

        try {
            Object service = getOrCreateService(serviceName, version, group, timeout);
            
            String[] paramTypes = getParamTypes(apiMetadata);
            Object[] paramValues = getParamValues(apiMetadata, params);
            
            log.debug("SOFA RPC调用: service={}, method={}, params={}", 
                serviceName, methodName, params);
            
            Class<?> serviceClass = service.getClass();
            Class<?>[] parameterTypes = getParameterTypes(paramTypes);
            Method method = serviceClass.getMethod(methodName, parameterTypes);
            Object result = method.invoke(service, paramValues);
            
            log.debug("SOFA RPC调用成功: service={}, method={}", serviceName, methodName);
            return result;
        } catch (Exception e) {
            log.error("SOFA RPC调用失败: service={}, method={}, error={}", 
                serviceName, methodName, e.getMessage(), e);
            throw new ProtocolConvertException("SOFA RPC调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取或创建服务引用
     */
    private Object getOrCreateService(String serviceName, String version, String group, int timeout) {
        String cacheKey = buildCacheKey(serviceName, version, group);
        
        return serviceCache.computeIfAbsent(cacheKey, key -> {
            try {
                if (referenceBuilderClass != null) {
                    return createSofaReference(serviceName, version, group, timeout);
                } else {
                    throw new ProtocolConvertException("SOFA RPC 依赖未引入，无法创建服务引用");
                }
            } catch (Exception e) {
                log.error("创建SOFA服务引用失败: {}", e.getMessage());
                throw new ProtocolConvertException("创建SOFA服务引用失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 创建SOFA服务引用
     */
    private Object createSofaReference(String serviceName, String version, String group, int timeout) throws Exception {
        Object builder = referenceBuilderClass.getDeclaredConstructor().newInstance();
        
        referenceBuilderClass.getMethod("setInterfaceClass", Class.class)
            .invoke(builder, Class.forName(serviceName));
        referenceBuilderClass.getMethod("setTimeout", int.class).invoke(builder, timeout);
        
        if (version != null && !version.isEmpty()) {
            referenceBuilderClass.getMethod("setVersion", String.class).invoke(builder, version);
        }
        if (group != null && !group.isEmpty()) {
            referenceBuilderClass.getMethod("setGroup", String.class).invoke(builder, group);
        }
        
        log.info("创建SOFA服务引用: service={}, version={}, group={}", serviceName, version, group);
        return referenceBuilderClass.getMethod("build").invoke(builder);
    }

    /**
     * 构建缓存Key
     */
    private String buildCacheKey(String serviceName, String version, String group) {
        StringBuilder key = new StringBuilder(serviceName);
        if (version != null && !version.isEmpty()) {
            key.append(":v:").append(version);
        }
        if (group != null && !group.isEmpty()) {
            key.append(":g:").append(group);
        }
        return key.toString();
    }

    /**
     * 获取参数类型数组
     */
    private String[] getParamTypes(ApiMetadata apiMetadata) {
        if (apiMetadata.getParamMappings() == null || apiMetadata.getParamMappings().isEmpty()) {
            return new String[0];
        }
        return apiMetadata.getParamMappings().values().stream()
            .map(ApiMetadata.ParamMapping::getParamType)
            .toArray(String[]::new);
    }

    /**
     * 获取参数值数组
     */
    private Object[] getParamValues(ApiMetadata apiMetadata, Map<String, Object> params) {
        if (apiMetadata.getParamMappings() == null || apiMetadata.getParamMappings().isEmpty()) {
            return new Object[0];
        }
        return apiMetadata.getParamMappings().values().stream()
            .map(mapping -> params.get(mapping.getHttpParam()))
            .toArray();
    }

    /**
     * 获取参数类型Class数组
     */
    private Class<?>[] getParameterTypes(String[] paramTypes) throws ClassNotFoundException {
        if (paramTypes == null || paramTypes.length == 0) {
            return new Class<?>[0];
        }
        Class<?>[] classes = new Class<?>[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            classes[i] = getClassForType(paramTypes[i]);
        }
        return classes;
    }

    /**
     * 根据类型名获取Class
     */
    private Class<?> getClassForType(String typeName) throws ClassNotFoundException {
        switch (typeName) {
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "double":
                return double.class;
            case "float":
                return float.class;
            case "boolean":
                return boolean.class;
            case "String":
            case "java.lang.String":
                return String.class;
            case "Integer":
            case "java.lang.Integer":
                return Integer.class;
            case "Long":
            case "java.lang.Long":
                return Long.class;
            case "Double":
            case "java.lang.Double":
                return Double.class;
            case "Boolean":
            case "java.lang.Boolean":
                return Boolean.class;
            default:
                return Class.forName(typeName);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getProtocolType() {
        return TYPE;
    }

    @Override
    public String[] discoverService(String serviceName) {
        return new String[]{serviceName};
    }

    @Override
    public void destroy() {
        log.info("销毁SOFAStack协议适配器");
        serviceCache.clear();
    }
}
