package com.osg.api.spi;

import com.osg.common.model.GatewayContext;

/**
 * 权限校验器SPI接口
 */
public interface Authorizer {

    /**
     * 校验权限
     * @param context 网关上下文
     * @param permission 需要的权限
     * @return 是否有权限
     */
    boolean authorize(GatewayContext context, String permission);

    /**
     * 校验角色
     * @param context 网关上下文
     * @param role 需要的角色
     * @return 是否有角色
     */
    boolean hasRole(GatewayContext context, String role);

    /**
     * 获取权限校验器名称
     * @return 权限校验器名称
     */
    String getName();

    /**
     * 获取权限校验器类型
     * @return 权限校验器类型代码
     */
    String getType();

    /**
     * 销毁权限校验器
     */
    void destroy();
}
