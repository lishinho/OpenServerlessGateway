package com.osg.ai;

import java.util.List;
import java.util.Map;

/**
 * AI 模型服务接口
 */
public interface AIModelService {

    /**
     * 预测流量
     * @param historicalData 历史流量数据
     * @param predictionWindow 预测时间窗口（分钟）
     * @return 预测结果
     */
    TrafficPrediction predictTraffic(List<TrafficData> historicalData, int predictionWindow);

    /**
     * 检测异常
     * @param currentData 当前数据
     * @param baseline 基线数据
     * @return 异常检测结果
     */
    AnomalyResult detectAnomaly(Map<String, Object> currentData, Map<String, Object> baseline);

    /**
     * 分析根因
     * @param incidentData 事件数据
     * @return 根因分析结果
     */
    RootCauseResult analyzeRootCause(Map<String, Object> incidentData);

    /**
     * 生成扩容建议
     * @param resourceUsage 资源使用情况
     * @param trafficTrend 流量趋势
     * @return 扩容建议
     */
    ScalingAdvice generateScalingAdvice(Map<String, Object> resourceUsage, List<TrafficData> trafficTrend);

    /**
     * 获取服务名称
     * @return 服务名称
     */
    String getName();

    /**
     * 获取模型类型
     * @return 模型类型
     */
    String getModelType();

    /**
     * 初始化服务
     * @param config 配置信息
     */
    void init(Map<String, Object> config);

    /**
     * 销毁服务
     */
    void destroy();
}
