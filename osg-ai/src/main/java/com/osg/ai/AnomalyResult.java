package com.osg.ai;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 异常检测结果
 */
@Data
public class AnomalyResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean anomaly;
    private double anomalyScore;
    private String anomalyType;
    private String severity;
    private String description;
    private long detectionTime;
    private List<AnomalyFeature> features;
    private Map<String, Object> context;

    @Data
    public static class AnomalyFeature implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private double actualValue;
        private double expectedValue;
        private double deviation;
        private double contribution;
    }
}
