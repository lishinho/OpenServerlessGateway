package com.osg.core.router;

import com.osg.api.spi.ProtocolAdapter;
import com.osg.common.exception.ProtocolConvertException;
import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;
import com.osg.core.plugin.PluginEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 请求路由引擎
 * 基于配置的请求转发、泛化调用执行
 */
@Component
public class RouterEngine {

    private static final Logger log = LoggerFactory.getLogger(RouterEngine.class);

    private final PluginEngine pluginEngine;

    public RouterEngine(PluginEngine pluginEngine) {
        this.pluginEngine = pluginEngine;
    }

    /**
     * 路由请求
     * @param context 网关上下文
     * @param apiMetadata API元数据
     * @param params 请求参数
     * @return 调用结果
     */
    public Object route(GatewayContext context, ApiMetadata apiMetadata, Map<String, Object> params) {
        String protocol = apiMetadata.getProtocol();
        ProtocolAdapter adapter = pluginEngine.getProtocolAdapter(protocol);
        
        if (adapter == null) {
            throw new ProtocolConvertException("不支持的协议类型: " + protocol);
        }

        log.debug("路由请求: traceId={}, path={}, protocol={}", 
            context.getTraceId(), context.getRequestPath(), protocol);

        try {
            return adapter.invoke(context, apiMetadata, params);
        } catch (Exception e) {
            log.error("路由请求失败: traceId={}, error={}", context.getTraceId(), e.getMessage(), e);
            throw new ProtocolConvertException("RPC调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 服务发现
     * @param protocol 协议类型
     * @param serviceName 服务名称
     * @return 服务地址列表
     */
    public String[] discoverService(String protocol, String serviceName) {
        ProtocolAdapter adapter = pluginEngine.getProtocolAdapter(protocol);
        if (adapter == null) {
            throw new ProtocolConvertException("不支持的协议类型: " + protocol);
        }
        return adapter.discoverService(serviceName);
    }

    /**
     * 检查服务是否可用
     * @param apiMetadata API元数据
     * @return 是否可用
     */
    public boolean isServiceAvailable(ApiMetadata apiMetadata) {
        String protocol = apiMetadata.getProtocol();
        ProtocolAdapter adapter = pluginEngine.getProtocolAdapter(protocol);
        if (adapter == null) {
            return false;
        }
        String[] addresses = adapter.discoverService(apiMetadata.getServiceName());
        return addresses != null && addresses.length > 0;
    }
}
