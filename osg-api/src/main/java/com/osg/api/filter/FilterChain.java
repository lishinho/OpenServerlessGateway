package com.osg.api.filter;

import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;

/**
 * 过滤器链接口
 */
public interface FilterChain {

    /**
     * 执行下一个过滤器
     * @param context 网关上下文
     * @param apiMetadata API元数据
     */
    void doFilter(GatewayContext context, ApiMetadata apiMetadata);

    /**
     * 是否还有下一个过滤器
     * @return 是否有下一个
     */
    boolean hasNext();

    /**
     * 重置过滤器链
     */
    void reset();
}
