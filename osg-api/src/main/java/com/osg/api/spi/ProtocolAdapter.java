package com.osg.api.spi;

import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;
import java.util.Map;

/**
 * 协议适配器SPI接口
 */
public interface ProtocolAdapter {

    /**
     * 初始化协议适配器
     * @param config 配置信息
     */
    void init(Map<String, Object> config);

    /**
     * 执行RPC调用
     * @param context 网关上下文
     * @param apiMetadata API元数据
     * @param params 调用参数
     * @return 调用结果
     */
    Object invoke(GatewayContext context, ApiMetadata apiMetadata, Map<String, Object> params);

    /**
     * 获取协议适配器名称
     * @return 协议适配器名称
     */
    String getName();

    /**
     * 获取支持的协议类型
     * @return 协议类型代码
     */
    String getProtocolType();

    /**
     * 服务发现
     * @param serviceName 服务名称
     * @return 服务地址列表
     */
    String[] discoverService(String serviceName);

    /**
     * 销毁协议适配器
     */
    void destroy();
}
