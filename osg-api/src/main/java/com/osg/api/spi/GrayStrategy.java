package com.osg.api.spi;

import com.osg.common.model.GatewayContext;

/**
 * 灰度策略SPI接口
 */
public interface GrayStrategy {

    /**
     * 判断是否命中灰度
     * @param context 网关上下文
     * @param ruleConfig 规则配置
     * @return 是否命中灰度
     */
    boolean match(GatewayContext context, String ruleConfig);

    /**
     * 获取灰度策略名称
     * @return 灰度策略名称
     */
    String getName();

    /**
     * 获取灰度策略类型
     * @return 灰度策略类型代码
     */
    String getType();

    /**
     * 销毁灰度策略
     */
    void destroy();
}
