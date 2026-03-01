package com.osg.protocol;

import com.osg.api.spi.ProtocolAdapter;
import com.osg.common.enums.ProtocolType;
import com.osg.common.exception.ProtocolConvertException;
import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dubbo协议适配器
 * 支持泛化调用，无需依赖业务接口包
 */
public class DubboProtocolAdapter implements ProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(DubboProtocolAdapter.class);

    private static final String NAME = "DubboProtocolAdapter";
    private static final String TYPE = ProtocolType.DUBBO.getCode();

    private ApplicationConfig applicationConfig;
    private RegistryConfig registryConfig;
    private final Map<String, ReferenceConfig<GenericService>> referenceCache = new ConcurrentHashMap<>();

    @Override
    public void init(Map<String, Object> config) {
        String appName = (String) config.getOrDefault("applicationName", "osg-gateway");
        String registryAddress = (String) config.get("registryAddress");
        
        applicationConfig = new ApplicationConfig();
        applicationConfig.setName(appName);
        
        if (registryAddress != null && !registryAddress.isEmpty()) {
            registryConfig = new RegistryConfig();
            registryConfig.setAddress(registryAddress);
        }
        
        log.info("初始化Dubbo协议适配器: appName={}, registry={}", appName, registryAddress);
    }

    @Override
    public Object invoke(GatewayContext context, ApiMetadata apiMetadata, Map<String, Object> params) {
        String serviceName = apiMetadata.getServiceName();
        String methodName = apiMetadata.getMethodName();
        String version = apiMetadata.getServiceVersion();
        String group = apiMetadata.getServiceGroup();
        int timeout = apiMetadata.getTimeout() > 0 ? apiMetadata.getTimeout() : 3000;

        try {
            GenericService genericService = getGenericService(serviceName, version, group, timeout);
            
            String[] paramTypes = getParamTypes(apiMetadata, params);
            Object[] paramValues = getParamValues(apiMetadata, params);
            
            log.debug("Dubbo泛化调用: service={}, method={}, params={}", 
                serviceName, methodName, params);
            
            Object result = genericService.$invoke(methodName, paramTypes, paramValues);
            
            log.debug("Dubbo泛化调用成功: service={}, method={}", serviceName, methodName);
            return result;
        } catch (Exception e) {
            log.error("Dubbo泛化调用失败: service={}, method={}, error={}", 
                serviceName, methodName, e.getMessage(), e);
            throw new ProtocolConvertException("Dubbo调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取泛化服务引用
     */
    private GenericService getGenericService(String serviceName, String version, String group, int timeout) {
        String cacheKey = buildCacheKey(serviceName, version, group);
        
        ReferenceConfig<GenericService> reference = referenceCache.get(cacheKey);
        if (reference == null || reference.get() == null) {
            reference = new ReferenceConfig<>();
            reference.setApplication(applicationConfig);
            if (registryConfig != null) {
                reference.setRegistry(registryConfig);
            }
            reference.setInterface(serviceName);
            reference.setGeneric("true");
            reference.setTimeout(timeout);
            
            if (version != null && !version.isEmpty()) {
                reference.setVersion(version);
            }
            if (group != null && !group.isEmpty()) {
                reference.setGroup(group);
            }
            
            referenceCache.put(cacheKey, reference);
            log.info("创建Dubbo泛化服务引用: service={}, version={}, group={}", serviceName, version, group);
        }
        
        return reference.get();
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
    private String[] getParamTypes(ApiMetadata apiMetadata, Map<String, Object> params) {
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
        log.info("销毁Dubbo协议适配器");
        for (ReferenceConfig<GenericService> reference : referenceCache.values()) {
            try {
                reference.destroy();
            } catch (Exception e) {
                log.error("销毁Dubbo服务引用失败: {}", e.getMessage());
            }
        }
        referenceCache.clear();
    }
}
