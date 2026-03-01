package com.osg.core.config;

import com.osg.common.model.ApiMetadata;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置解析引擎
 * 负责动态加载配置和热更新
 */
@Component
@RefreshScope
public class ConfigEngine {

    private final Map<String, ApiMetadata> apiMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, Object> globalConfig = new ConcurrentHashMap<>();

    /**
     * 获取API元数据
     * @param apiId API标识
     * @return API元数据
     */
    public ApiMetadata getApiMetadata(String apiId) {
        return apiMetadataCache.get(apiId);
    }

    /**
     * 根据路径和方法获取API元数据
     * @param path 请求路径
     * @param method HTTP方法
     * @return API元数据
     */
    public ApiMetadata getApiMetadataByPath(String path, String method) {
        for (ApiMetadata metadata : apiMetadataCache.values()) {
            if (matchPath(metadata.getHttpPath(), path) 
                && metadata.getHttpMethod().equalsIgnoreCase(method)) {
                return metadata;
            }
        }
        return null;
    }

    /**
     * 注册API元数据
     * @param apiMetadata API元数据
     */
    public void registerApiMetadata(ApiMetadata apiMetadata) {
        apiMetadataCache.put(apiMetadata.getApiId(), apiMetadata);
    }

    /**
     * 移除API元数据
     * @param apiId API标识
     */
    public void removeApiMetadata(String apiId) {
        apiMetadataCache.remove(apiId);
    }

    /**
     * 刷新配置
     * @param configMap 配置映射
     */
    public void refreshConfig(Map<String, Object> configMap) {
        globalConfig.clear();
        globalConfig.putAll(configMap);
    }

    /**
     * 获取全局配置
     * @param key 配置键
     * @return 配置值
     */
    public Object getGlobalConfig(String key) {
        return globalConfig.get(key);
    }

    /**
     * 获取全局配置
     * @param key 配置键
     * @param defaultValue 默认值
     * @param <T> 值类型
     * @return 配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getGlobalConfig(String key, T defaultValue) {
        Object value = globalConfig.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    /**
     * 路径匹配
     * @param pattern 匹配模式
     * @param path 实际路径
     * @return 是否匹配
     */
    private boolean matchPath(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        if (pattern.contains("{") && pattern.contains("}")) {
            return matchPathWithVariables(pattern, path);
        }
        return pattern.equals(path);
    }

    /**
     * 带变量的路径匹配
     * @param pattern 匹配模式
     * @param path 实际路径
     * @return 是否匹配
     */
    private boolean matchPathWithVariables(String pattern, String path) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        if (patternParts.length != pathParts.length) {
            return false;
        }
        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];
            if (!patternPart.startsWith("{") || !patternPart.endsWith("}")) {
                if (!patternPart.equals(pathPart)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 获取所有API元数据
     * @return API元数据映射
     */
    public Map<String, ApiMetadata> getAllApiMetadata() {
        return new ConcurrentHashMap<>(apiMetadataCache);
    }

    /**
     * 清空所有配置
     */
    public void clear() {
        apiMetadataCache.clear();
        globalConfig.clear();
    }
}
