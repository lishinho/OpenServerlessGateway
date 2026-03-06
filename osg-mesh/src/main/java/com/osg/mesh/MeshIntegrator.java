package com.osg.mesh;

import java.util.Map;

/**
 * 服务网格集成器接口
 */
public interface MeshIntegrator {

    /**
     * 注册服务到网格
     * @param serviceName 服务名称
     * @param serviceInfo 服务信息
     * @return 是否成功
     */
    boolean registerService(String serviceName, Map<String, Object> serviceInfo);

    /**
     * 注销服务
     * @param serviceName 服务名称
     * @return 是否成功
     */
    boolean deregisterService(String serviceName);

    /**
     * 配置流量规则
     * @param ruleName 规则名称
     * @param ruleConfig 规则配置
     * @return 是否成功
     */
    boolean configureTrafficRule(String ruleName, Map<String, Object> ruleConfig);

    /**
     * 删除流量规则
     * @param ruleName 规则名称
     * @return 是否成功
     */
    boolean deleteTrafficRule(String ruleName);

    /**
     * 获取服务状态
     * @param serviceName 服务名称
     * @return 服务状态
     */
    Map<String, Object> getServiceStatus(String serviceName);

    /**
     * 获取集成器名称
     * @return 集成器名称
     */
    String getName();

    /**
     * 获取网格类型
     * @return 网格类型代码
     */
    String getMeshType();

    /**
     * 初始化集成器
     * @param config 配置信息
     */
    void init(Map<String, Object> config);

    /**
     * 销毁集成器
     */
    void destroy();
}
