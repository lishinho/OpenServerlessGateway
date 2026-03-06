package com.osg.serverless;

import com.osg.common.model.GatewayContext;

/**
 * Serverless函数调用器接口
 */
public interface FunctionInvoker {

    /**
     * 调用函数
     * @param context 网关上下文
     * @param functionName 函数名称
     * @param payload 调用参数
     * @return 调用结果
     */
    Object invoke(GatewayContext context, String functionName, Object payload);

    /**
     * 异步调用函数
     * @param context 网关上下文
     * @param functionName 函数名称
     * @param payload 调用参数
     * @return 调用请求ID
     */
    String invokeAsync(GatewayContext context, String functionName, Object payload);

    /**
     * 获取调用器名称
     * @return 调用器名称
     */
    String getName();

    /**
     * 获取平台类型
     * @return 平台类型代码
     */
    String getPlatformType();

    /**
     * 初始化调用器
     * @param config 配置信息
     */
    void init(java.util.Map<String, Object> config);

    /**
     * 销毁调用器
     */
    void destroy();
}
