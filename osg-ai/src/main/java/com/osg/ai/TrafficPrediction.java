package com.osg.ai;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 流量预测结果
 */
@Data
public class TrafficPrediction implements Serializable {

    private static final long serialVersionUID = 1L;

    private long predictionTime;
    private String apiId;
    private double predictedQps;
    private double predictedLatency;
    private double confidence;
    private List<PredictionPoint> predictionPoints;
    private String trend;
    private String recommendation;

    @Data
    public static class PredictionPoint implements Serializable {
        private static final long serialVersionUID = 1L;
        private long timestamp;
        private double value;
        private double lowerBound;
        private double upperBound;
    }
}
