package com.osg.api.spi;

import com.osg.common.model.GatewayContext;

/**
 * 认证器SPI接口
 */
public interface Authenticator {

    /**
     * 认证请求
     * @param context 网关上下文
     * @param token 认证令牌
     * @return 认证是否成功
     */
    boolean authenticate(GatewayContext context, String token);

    /**
     * 获取认证器名称
     * @return 认证器名称
     */
    String getName();

    /**
     * 获取认证器类型
     * @return 认证器类型代码
     */
    String getType();

    /**
     * 刷新令牌
     * @param token 原令牌
     * @return 新令牌
     */
    String refreshToken(String token);

    /**
     * 验证令牌是否有效
     * @param token 令牌
     * @return 是否有效
     */
    boolean validateToken(String token);

    /**
     * 销毁认证器
     */
    void destroy();
}
