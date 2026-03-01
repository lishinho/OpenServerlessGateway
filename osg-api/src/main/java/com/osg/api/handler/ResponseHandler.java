package com.osg.api.handler;

import com.osg.common.model.GatewayContext;

/**
 * 响应处理器接口
 */
public interface ResponseHandler {

    /**
     * 处理响应
     * @param context 网关上下文
     * @param result 原始结果
     * @return 处理后的结果
     */
    Object handle(GatewayContext context, Object result);

    /**
     * 处理异常
     * @param context 网关上下文
     * @param throwable 异常
     * @return 异常处理结果
     */
    Object handleException(GatewayContext context, Throwable throwable);

    /**
     * 获取处理器名称
     * @return 处理器名称
     */
    String getName();
}
