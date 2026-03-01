package com.osg.api.handler;

import com.osg.common.model.GatewayContext;

/**
 * 请求处理器接口
 */
public interface RequestHandler {

    /**
     * 处理请求
     * @param context 网关上下文
     * @return 处理结果
     */
    Object handle(GatewayContext context);

    /**
     * 获取处理器名称
     * @return 处理器名称
     */
    String getName();

    /**
     * 是否支持该请求
     * @param context 网关上下文
     * @return 是否支持
     */
    boolean supports(GatewayContext context);
}
