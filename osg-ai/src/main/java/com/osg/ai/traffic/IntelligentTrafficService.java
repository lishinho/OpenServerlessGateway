package com.osg.ai.traffic;

import com.osg.ai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能流量管控服务
 * 基于 AI 的流量预测和自适应限流
 */
public class IntelligentTrafficService implements AIModelService {

    private static final Logger log = LoggerFactory.getLogger(IntelligentTrafficService.class);

    private static final String NAME = "IntelligentTrafficService";
    private static final String MODEL_TYPE = "traffic_prediction";

    private int historyWindowSize;
    private double anomalyThreshold;
    private double scalingThreshold;
    private Map<String, List<TrafficData>> trafficHistory;

    public IntelligentTrafficService() {
        this.historyWindowSize = 60;
        this.anomalyThreshold = 0.8;
        this.scalingThreshold = 0.7;
        this.trafficHistory = new ConcurrentHashMap<>();
    }

    @Override
    public void init(Map<String, Object> config) {
        if (config.containsKey("historyWindowSize")) {
            this.historyWindowSize = (Integer) config.get("historyWindowSize");
        }
        if (config.containsKey("anomalyThreshold")) {
            this.anomalyThreshold = (Double) config.get("anomalyThreshold");
        }
        if (config.containsKey("scalingThreshold")) {
            this.scalingThreshold = (Double) config.get("scalingThreshold");
        }

        log.info("初始化智能流量管控服务: historyWindow={}, anomalyThreshold={}", 
            historyWindowSize, anomalyThreshold);
    }

    @Override
    public TrafficPrediction predictTraffic(List<TrafficData> historicalData, int predictionWindow) {
        log.debug("预测流量: dataPoints={}, window={}min", historicalData.size(), predictionWindow);

        if (historicalData == null || historicalData.isEmpty()) {
            return createEmptyPrediction();
        }

        TrafficPrediction prediction = new TrafficPrediction();
        prediction.setPredictionTime(System.currentTimeMillis());
        prediction.setPredictionPoints(new ArrayList<>());

        double avgQps = calculateAverageQps(historicalData);
        double avgLatency = calculateAverageLatency(historicalData);
        double trend = calculateTrend(historicalData);

        prediction.setPredictedQps(avgQps * (1 + trend * predictionWindow / 60.0));
        prediction.setPredictedLatency(avgLatency);
        prediction.setConfidence(calculateConfidence(historicalData));
        prediction.setTrend(trend > 0 ? "上升" : trend < 0 ? "下降" : "平稳");

        generatePredictionPoints(prediction, historicalData, predictionWindow);
        generateRecommendation(prediction);

        return prediction;
    }

    /**
     * 计算平均 QPS
     */
    private double calculateAverageQps(List<TrafficData> data) {
        return data.stream()
            .mapToDouble(TrafficData::getRequestCount)
            .average()
            .orElse(0.0) / 60.0;
    }

    /**
     * 计算平均延迟
     */
    private double calculateAverageLatency(List<TrafficData> data) {
        return data.stream()
            .mapToDouble(TrafficData::getAvgLatency)
            .average()
            .orElse(0.0);
    }

    /**
     * 计算趋势
     */
    private double calculateTrend(List<TrafficData> data) {
        if (data.size() < 2) {
            return 0.0;
        }

        int n = data.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += data.get(i).getRequestCount();
            sumXY += i * data.get(i).getRequestCount();
            sumX2 += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope / (sumY / n);
    }

    /**
     * 计算置信度
     */
    private double calculateConfidence(List<TrafficData> data) {
        double variance = calculateVariance(data);
        double mean = calculateAverageQps(data) * 60;
        
        if (mean == 0) {
            return 0.5;
        }
        
        double cv = Math.sqrt(variance) / mean;
        return Math.max(0.1, 1.0 - cv);
    }

    /**
     * 计算方差
     */
    private double calculateVariance(List<TrafficData> data) {
        double mean = data.stream()
            .mapToDouble(TrafficData::getRequestCount)
            .average()
            .orElse(0.0);
        
        return data.stream()
            .mapToDouble(d -> Math.pow(d.getRequestCount() - mean, 2))
            .average()
            .orElse(0.0);
    }

    /**
     * 生成预测点
     */
    private void generatePredictionPoints(TrafficPrediction prediction, List<TrafficData> data, int window) {
        long baseTime = System.currentTimeMillis();
        double baseQps = prediction.getPredictedQps();
        double trend = calculateTrend(data);

        for (int i = 0; i < window; i++) {
            TrafficPrediction.PredictionPoint point = new TrafficPrediction.PredictionPoint();
            point.setTimestamp(baseTime + i * 60000L);
            
            double predicted = baseQps * (1 + trend * i / 60.0);
            point.setValue(predicted);
            point.setLowerBound(predicted * 0.9);
            point.setUpperBound(predicted * 1.1);
            
            prediction.getPredictionPoints().add(point);
        }
    }

    /**
     * 生成建议
     */
    private void generateRecommendation(TrafficPrediction prediction) {
        StringBuilder sb = new StringBuilder();
        
        if (prediction.getPredictedQps() > 10000) {
            sb.append("预测流量较高，建议增加实例数；");
        }
        
        if ("上升".equals(prediction.getTrend())) {
            sb.append("流量呈上升趋势，建议提前扩容；");
        }
        
        if (prediction.getConfidence() < 0.5) {
            sb.append("预测置信度较低，建议人工介入评估；");
        }
        
        if (sb.length() == 0) {
            sb.append("流量正常，无需特殊处理");
        }
        
        prediction.setRecommendation(sb.toString());
    }

    /**
     * 创建空预测
     */
    private TrafficPrediction createEmptyPrediction() {
        TrafficPrediction prediction = new TrafficPrediction();
        prediction.setPredictionTime(System.currentTimeMillis());
        prediction.setPredictedQps(0);
        prediction.setPredictedLatency(0);
        prediction.setConfidence(0);
        prediction.setTrend("未知");
        prediction.setPredictionPoints(new ArrayList<>());
        prediction.setRecommendation("无历史数据，无法预测");
        return prediction;
    }

    @Override
    public AnomalyResult detectAnomaly(Map<String, Object> currentData, Map<String, Object> baseline) {
        AnomalyResult result = new AnomalyResult();
        result.setDetectionTime(System.currentTimeMillis());
        result.setFeatures(new ArrayList<>());
        result.setContext(currentData);

        double totalScore = 0;
        List<String> detectedAnomalies = new ArrayList<>();

        for (Map.Entry<String, Object> entry : currentData.entrySet()) {
            String key = entry.getKey();
            Object currentValue = entry.getValue();
            Object baselineValue = baseline.get(key);

            if (baselineValue == null) {
                continue;
            }

            AnomalyResult.AnomalyFeature feature = analyzeFeature(key, currentValue, baselineValue);
            if (feature != null) {
                result.getFeatures().add(feature);
                totalScore += feature.getContribution();
                
                if (feature.getDeviation() > anomalyThreshold) {
                    detectedAnomalies.add(key);
                }
            }
        }

        result.setAnomaly(totalScore > anomalyThreshold);
        result.setAnomalyScore(totalScore);
        result.setAnomalyType(determineAnomalyType(detectedAnomalies));
        result.setSeverity(determineSeverity(totalScore));
        result.setDescription(generateDescription(detectedAnomalies, totalScore));

        return result;
    }

    /**
     * 分析特征
     */
    private AnomalyResult.AnomalyFeature analyzeFeature(String name, Object current, Object baseline) {
        try {
            double currentVal = ((Number) current).doubleValue();
            double baselineVal = ((Number) baseline).doubleValue();

            if (baselineVal == 0) {
                return null;
            }

            AnomalyResult.AnomalyFeature feature = new AnomalyResult.AnomalyFeature();
            feature.setName(name);
            feature.setActualValue(currentVal);
            feature.setExpectedValue(baselineVal);
            feature.setDeviation(Math.abs(currentVal - baselineVal) / baselineVal);
            feature.setContribution(feature.getDeviation() * getFeatureWeight(name));

            return feature;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取特征权重
     */
    private double getFeatureWeight(String featureName) {
        switch (featureName) {
            case "errorRate":
                return 2.0;
            case "latency":
                return 1.5;
            case "qps":
                return 1.0;
            default:
                return 0.5;
        }
    }

    /**
     * 确定异常类型
     */
    private String determineAnomalyType(List<String> anomalies) {
        if (anomalies.contains("errorRate")) {
            return "错误率异常";
        }
        if (anomalies.contains("latency")) {
            return "延迟异常";
        }
        if (anomalies.contains("qps")) {
            return "流量异常";
        }
        return anomalies.isEmpty() ? "正常" : "综合异常";
    }

    /**
     * 确定严重程度
     */
    private String determineSeverity(double score) {
        if (score > 2.0) {
            return "严重";
        }
        if (score > 1.0) {
            return "高";
        }
        if (score > 0.5) {
            return "中";
        }
        return "低";
    }

    /**
     * 生成描述
     */
    private String generateDescription(List<String> anomalies, double score) {
        if (anomalies.isEmpty()) {
            return "系统运行正常";
        }
        return String.format("检测到异常指标: %s，异常评分: %.2f", 
            String.join(", ", anomalies), score);
    }

    @Override
    public RootCauseResult analyzeRootCause(Map<String, Object> incidentData) {
        RootCauseResult result = new RootCauseResult();
        result.setIncidentId(UUID.randomUUID().toString());
        result.setAnalysisTime(System.currentTimeMillis());
        result.setRootCauses(new ArrayList<>());
        result.setSuggestedActions(new ArrayList<>());

        analyzeErrorPatterns(result, incidentData);
        analyzeResourceUsage(result, incidentData);
        analyzeDependencies(result, incidentData);

        result.setSummary(generateSummary(result.getRootCauses()));
        result.setConfidence(calculateRootCauseConfidence(result.getRootCauses()));

        return result;
    }

    /**
     * 分析错误模式
     */
    private void analyzeErrorPatterns(RootCauseResult result, Map<String, Object> data) {
        Double errorRate = (Double) data.get("errorRate");
        if (errorRate != null && errorRate > 0.1) {
            RootCauseResult.RootCause cause = new RootCauseResult.RootCause();
            cause.setComponent("服务层");
            cause.setCause("错误率过高");
            cause.setProbability(0.8);
            cause.setEvidence(Arrays.asList("错误率: " + errorRate));
            cause.setImpact("影响用户体验");
            result.getRootCauses().add(cause);
            result.getSuggestedActions().add("检查后端服务日志");
        }
    }

    /**
     * 分析资源使用
     */
    private void analyzeResourceUsage(RootCauseResult result, Map<String, Object> data) {
        Double cpuUsage = (Double) data.get("cpuUsage");
        Double memoryUsage = (Double) data.get("memoryUsage");

        if (cpuUsage != null && cpuUsage > 0.8) {
            RootCauseResult.RootCause cause = new RootCauseResult.RootCause();
            cause.setComponent("资源层");
            cause.setCause("CPU 使用率过高");
            cause.setProbability(0.7);
            cause.setEvidence(Arrays.asList("CPU: " + cpuUsage * 100 + "%"));
            cause.setImpact("性能下降");
            result.getRootCauses().add(cause);
            result.getSuggestedActions().add("扩容或优化代码");
        }

        if (memoryUsage != null && memoryUsage > 0.85) {
            RootCauseResult.RootCause cause = new RootCauseResult.RootCause();
            cause.setComponent("资源层");
            cause.setCause("内存使用率过高");
            cause.setProbability(0.7);
            cause.setEvidence(Arrays.asList("内存: " + memoryUsage * 100 + "%"));
            cause.setImpact("可能OOM");
            result.getRootCauses().add(cause);
            result.getSuggestedActions().add("扩容或排查内存泄漏");
        }
    }

    /**
     * 分析依赖
     */
    private void analyzeDependencies(RootCauseResult result, Map<String, Object> data) {
        Double dbLatency = (Double) data.get("dbLatency");
        if (dbLatency != null && dbLatency > 100) {
            RootCauseResult.RootCause cause = new RootCauseResult.RootCause();
            cause.setComponent("数据库");
            cause.setCause("数据库响应慢");
            cause.setProbability(0.6);
            cause.setEvidence(Arrays.asList("DB延迟: " + dbLatency + "ms"));
            cause.setImpact("请求延迟增加");
            result.getRootCauses().add(cause);
            result.getSuggestedActions().add("优化数据库查询");
        }
    }

    /**
     * 生成摘要
     */
    private String generateSummary(List<RootCauseResult.RootCause> causes) {
        if (causes.isEmpty()) {
            return "未发现明显根因";
        }
        return causes.stream()
            .map(c -> c.getComponent() + ": " + c.getCause())
            .collect(Collectors.joining("; "));
    }

    /**
     * 计算根因置信度
     */
    private double calculateRootCauseConfidence(List<RootCauseResult.RootCause> causes) {
        if (causes.isEmpty()) {
            return 0;
        }
        return causes.stream()
            .mapToDouble(RootCauseResult.RootCause::getProbability)
            .average()
            .orElse(0);
    }

    @Override
    public ScalingAdvice generateScalingAdvice(Map<String, Object> resourceUsage, List<TrafficData> trafficTrend) {
        ScalingAdvice advice = new ScalingAdvice();
        advice.setAdviceTime(System.currentTimeMillis());
        advice.setFactors(new ArrayList<>());

        Integer currentReplicas = (Integer) resourceUsage.getOrDefault("currentReplicas", 1);
        advice.setCurrentReplicas(currentReplicas);

        double cpuFactor = analyzeCpuFactor(resourceUsage, advice);
        double memoryFactor = analyzeMemoryFactor(resourceUsage, advice);
        double trafficFactor = analyzeTrafficFactor(trafficTrend, advice);

        double totalFactor = cpuFactor + memoryFactor + trafficFactor;

        if (totalFactor > scalingThreshold) {
            advice.setAction("扩容");
            advice.setRecommendedReplicas((int) Math.ceil(currentReplicas * 1.5));
            advice.setReason("资源使用率或流量趋势超过阈值");
        } else if (totalFactor < 0.3) {
            advice.setAction("缩容");
            advice.setRecommendedReplicas(Math.max(1, currentReplicas - 1));
            advice.setReason("资源使用率低，可节省成本");
        } else {
            advice.setAction("保持");
            advice.setRecommendedReplicas(currentReplicas);
            advice.setReason("资源使用正常");
        }

        advice.setUrgency(totalFactor);
        advice.setConfidence(0.8);

        return advice;
    }

    /**
     * 分析 CPU 因素
     */
    private double analyzeCpuFactor(Map<String, Object> usage, ScalingAdvice advice) {
        Double cpuUsage = (Double) usage.get("cpuUsage");
        if (cpuUsage == null) {
            return 0;
        }

        ScalingAdvice.ScalingFactor factor = new ScalingAdvice.ScalingFactor();
        factor.setName("CPU使用率");
        factor.setCurrentValue(cpuUsage);
        factor.setThreshold(0.7);
        factor.setWeight(0.4);
        factor.setImpact(cpuUsage > 0.7 ? "需要扩容" : "正常");

        advice.getFactors().add(factor);

        return cpuUsage > 0.7 ? cpuUsage * factor.getWeight() : 0;
    }

    /**
     * 分析内存因素
     */
    private double analyzeMemoryFactor(Map<String, Object> usage, ScalingAdvice advice) {
        Double memoryUsage = (Double) usage.get("memoryUsage");
        if (memoryUsage == null) {
            return 0;
        }

        ScalingAdvice.ScalingFactor factor = new ScalingAdvice.ScalingFactor();
        factor.setName("内存使用率");
        factor.setCurrentValue(memoryUsage);
        factor.setThreshold(0.8);
        factor.setWeight(0.3);
        factor.setImpact(memoryUsage > 0.8 ? "需要扩容" : "正常");

        advice.getFactors().add(factor);

        return memoryUsage > 0.8 ? memoryUsage * factor.getWeight() : 0;
    }

    /**
     * 分析流量因素
     */
    private double analyzeTrafficFactor(List<TrafficData> trend, ScalingAdvice advice) {
        if (trend == null || trend.isEmpty()) {
            return 0;
        }

        double trafficTrend = calculateTrend(trend);
        
        ScalingAdvice.ScalingFactor factor = new ScalingAdvice.ScalingFactor();
        factor.setName("流量趋势");
        factor.setCurrentValue(trafficTrend);
        factor.setThreshold(0.2);
        factor.setWeight(0.3);
        factor.setImpact(trafficTrend > 0.2 ? "流量上升" : "流量平稳");

        advice.getFactors().add(factor);

        return trafficTrend > 0.2 ? trafficTrend * factor.getWeight() : 0;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getModelType() {
        return MODEL_TYPE;
    }

    @Override
    public void destroy() {
        log.info("销毁智能流量管控服务");
        trafficHistory.clear();
    }
}
