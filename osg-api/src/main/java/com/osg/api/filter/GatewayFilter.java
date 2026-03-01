package com.osg.api.filter;

import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;

/**
 * 网关过滤器接口
 */
public interface GatewayFilter {

    /**
     * 过滤器执行顺序，数值越小优先级越高
     * @return 顺序值
     */
    int getOrder();

    /**
     * 过滤器名称
     * @return 名称
     */
    String getName();

    /**
     * 执行过滤逻辑
     * @param context 网关上下文
     * @param apiMetadata API元数据
     * @param chain 过滤器链
     */
    void doFilter(GatewayContext context, ApiMetadata apiMetadata, FilterChain chain);

    /**
     * 是否启用
     * @return 是否启用
     */
    boolean isEnabled();
}
