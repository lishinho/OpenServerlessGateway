package com.osg.cloud;

import java.util.Map;

/**
 * 云平台适配器接口
 */
public interface CloudAdapter {

    /**
     * 部署网关实例
     * @param deploymentName 部署名称
     * @param config 部署配置
     * @return 是否成功
     */
    boolean deploy(String deploymentName, Map<String, Object> config);

    /**
     * 扩缩容网关实例
     * @param deploymentName 部署名称
     * @param replicas 副本数
     * @return 是否成功
     */
    boolean scale(String deploymentName, int replicas);

    /**
     * 获取部署状态
     * @param deploymentName 部署名称
     * @return 部署状态
     */
    Map<String, Object> getStatus(String deploymentName);

    /**
     * 删除部署
     * @param deploymentName 部署名称
     * @return 是否成功
     */
    boolean delete(String deploymentName);

    /**
     * 获取适配器名称
     * @return 适配器名称
     */
    String getName();

    /**
     * 获取云平台类型
     * @return 云平台类型代码
     */
    String getCloudType();

    /**
     * 初始化适配器
     * @param config 配置信息
     */
    void init(Map<String, Object> config);

    /**
     * 销毁适配器
     */
    void destroy();
}
