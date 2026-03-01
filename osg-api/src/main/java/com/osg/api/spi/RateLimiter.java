package com.osg.api.spi;

import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;

/**
 * 限流器SPI接口
 */
public interface RateLimiter {

    /**
     * 初始化限流器
     * @param config 限流配置
     */
    void init(ApiMetadata.RateLimitConfig config);

    /**
     * 尝试获取许可
     * @param context 网关上下文
     * @param apiMetadata API元数据
     * @return 是否允许通过
     */
    boolean tryAcquire(GatewayContext context, ApiMetadata apiMetadata);

    /**
     * 获取限流器名称
     * @return 限流器名称
     */
    String getName();

    /**
     * 获取限流器类型
     * @return 限流器类型代码
     */
    String getType();

    /**
     * 销毁限流器
     */
    void destroy();
}
