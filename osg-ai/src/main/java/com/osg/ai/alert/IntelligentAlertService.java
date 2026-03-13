package com.osg.ai.alert;

import com.osg.ai.AnomalyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智能告警降噪服务
 * 基于 AI 的告警聚合、去重和降噪
 */
public class IntelligentAlertService {

    private static final Logger log = LoggerFactory.getLogger(IntelligentAlertService.class);

    private int dedupWindowMinutes;
    private int aggregationWindowMinutes;
    private double similarityThreshold;
    private Map<String, AlertContext> alertCache;
    private AtomicInteger alertCounter;

    public IntelligentAlertService() {
        this.dedupWindowMinutes = 5;
        this.aggregationWindowMinutes = 10;
        this.similarityThreshold = 0.8;
        this.alertCache = new ConcurrentHashMap<>();
        this.alertCounter = new AtomicInteger(0);
    }

    /**
     * 初始化服务
     */
    public void init(Map<String, Object> config) {
        if (config.containsKey("dedupWindowMinutes")) {
            this.dedupWindowMinutes = (Integer) config.get("dedupWindowMinutes");
        }
        if (config.containsKey("aggregationWindowMinutes")) {
            this.aggregationWindowMinutes = (Integer) config.get("aggregationWindowMinutes");
        }
        if (config.containsKey("similarityThreshold")) {
            this.similarityThreshold = (Double) config.get("similarityThreshold");
        }

        log.info("初始化智能告警降噪服务: dedupWindow={}min, aggregationWindow={}min", 
            dedupWindowMinutes, aggregationWindowMinutes);
    }

    /**
     * 处理告警
     * @param anomalyResult 异常检测结果
     * @return 处理后的告警
     */
    public Alert processAlert(AnomalyResult anomalyResult) {
        String alertKey = generateAlertKey(anomalyResult);
        
        AlertContext context = alertCache.computeIfAbsent(alertKey, k -> new AlertContext());
        
        if (isDuplicate(context, anomalyResult)) {
            log.debug("告警去重: key={}", alertKey);
            context.incrementDuplicateCount();
            return null;
        }

        Alert alert = createAlert(anomalyResult);
        
        List<Alert> similarAlerts = findSimilarAlerts(alert);
        if (!similarAlerts.isEmpty()) {
            alert = aggregateAlerts(alert, similarAlerts);
            log.info("告警聚合: alertId={}, aggregatedCount={}", alert.getAlertId(), similarAlerts.size());
        }

        context.updateLastAlert(alert);
        
        return alert;
    }

    /**
     * 生成告警Key
     */
    private String generateAlertKey(AnomalyResult anomalyResult) {
        return anomalyResult.getAnomalyType() + ":" + 
               anomalyResult.getSeverity() + ":" +
               anomalyResult.getFeatures().stream()
                   .map(f -> f.getName())
                   .reduce((a, b) -> a + "," + b)
                   .orElse("");
    }

    /**
     * 判断是否重复
     */
    private boolean isDuplicate(AlertContext context, AnomalyResult anomalyResult) {
        if (context.getLastAlertTime() == 0) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - context.getLastAlertTime();
        return elapsed < dedupWindowMinutes * 60 * 1000L;
    }

    /**
     * 创建告警
     */
    private Alert createAlert(AnomalyResult anomalyResult) {
        Alert alert = new Alert();
        alert.setAlertId("alert-" + alertCounter.incrementAndGet());
        alert.setAlertTime(System.currentTimeMillis());
        alert.setAnomalyType(anomalyResult.getAnomalyType());
        alert.setSeverity(anomalyResult.getSeverity());
        alert.setDescription(anomalyResult.getDescription());
        alert.setAnomalyScore(anomalyResult.getAnomalyScore());
        alert.setFeatures(anomalyResult.getFeatures());
        alert.setContext(anomalyResult.getContext());
        alert.setStatus("NEW");
        
        return alert;
    }

    /**
     * 查找相似告警
     */
    private List<Alert> findSimilarAlerts(Alert newAlert) {
        List<Alert> similar = new ArrayList<>();
        long windowStart = System.currentTimeMillis() - aggregationWindowMinutes * 60 * 1000L;

        for (AlertContext context : alertCache.values()) {
            Alert lastAlert = context.getLastAlert();
            if (lastAlert != null && lastAlert.getAlertTime() > windowStart) {
                double similarity = calculateSimilarity(newAlert, lastAlert);
                if (similarity > similarityThreshold) {
                    similar.add(lastAlert);
                }
            }
        }

        return similar;
    }

    /**
     * 计算相似度
     */
    private double calculateSimilarity(Alert a1, Alert a2) {
        if (!a1.getAnomalyType().equals(a2.getAnomalyType())) {
            return 0;
        }

        double typeScore = 1.0;
        double severityScore = a1.getSeverity().equals(a2.getSeverity()) ? 1.0 : 0.5;
        double featureScore = calculateFeatureSimilarity(a1.getFeatures(), a2.getFeatures());

        return (typeScore + severityScore + featureScore) / 3.0;
    }

    /**
     * 计算特征相似度
     */
    private double calculateFeatureSimilarity(List<AnomalyResult.AnomalyFeature> f1, 
                                               List<AnomalyResult.AnomalyFeature> f2) {
        if (f1.isEmpty() || f2.isEmpty()) {
            return 0;
        }

        Set<String> names1 = new HashSet<>();
        f1.forEach(f -> names1.add(f.getName()));

        Set<String> names2 = new HashSet<>();
        f2.forEach(f -> names2.add(f.getName()));

        Set<String> intersection = new HashSet<>(names1);
        intersection.retainAll(names2);

        Set<String> union = new HashSet<>(names1);
        union.addAll(names2);

        return (double) intersection.size() / union.size();
    }

    /**
     * 聚合告警
     */
    private Alert aggregateAlerts(Alert primary, List<Alert> similar) {
        Alert aggregated = new Alert();
        aggregated.setAlertId(primary.getAlertId());
        aggregated.setAlertTime(System.currentTimeMillis());
        aggregated.setAnomalyType(primary.getAnomalyType());
        aggregated.setSeverity(determineAggregatedSeverity(primary, similar));
        aggregated.setDescription(primary.getDescription() + " (聚合" + similar.size() + "条相似告警)");
        aggregated.setAnomalyScore(primary.getAnomalyScore());
        aggregated.setFeatures(primary.getFeatures());
        aggregated.setContext(primary.getContext());
        aggregated.setStatus("AGGREGATED");
        aggregated.setAggregatedCount(similar.size() + 1);

        return aggregated;
    }

    /**
     * 确定聚合后严重程度
     */
    private String determineAggregatedSeverity(Alert primary, List<Alert> similar) {
        long severeCount = similar.stream()
            .filter(a -> "严重".equals(a.getSeverity()) || "高".equals(a.getSeverity()))
            .count();

        if (severeCount > similar.size() / 2) {
            return "严重";
        }
        return primary.getSeverity();
    }

    /**
     * 获取告警统计
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAlerts", alertCounter.get());
        stats.put("activeKeys", alertCache.size());
        
        long dedupedCount = alertCache.values().stream()
            .mapToLong(c -> c.getDuplicateCount())
            .sum();
        stats.put("dedupedAlerts", dedupedCount);
        
        return stats;
    }

    /**
     * 清理过期告警
     */
    public void cleanup() {
        long expireTime = System.currentTimeMillis() - aggregationWindowMinutes * 60 * 1000L * 2;
        alertCache.entrySet().removeIf(e -> e.getValue().getLastAlertTime() < expireTime);
        log.debug("清理过期告警上下文: remaining={}", alertCache.size());
    }

    /**
     * 销毁服务
     */
    public void destroy() {
        log.info("销毁智能告警降噪服务");
        alertCache.clear();
    }

    /**
     * 告警上下文
     */
    private static class AlertContext {
        private long lastAlertTime;
        private Alert lastAlert;
        private int duplicateCount;

        public long getLastAlertTime() {
            return lastAlertTime;
        }

        public Alert getLastAlert() {
            return lastAlert;
        }

        public int getDuplicateCount() {
            return duplicateCount;
        }

        public void incrementDuplicateCount() {
            this.duplicateCount++;
        }

        public void updateLastAlert(Alert alert) {
            this.lastAlert = alert;
            this.lastAlertTime = System.currentTimeMillis();
        }
    }
}
