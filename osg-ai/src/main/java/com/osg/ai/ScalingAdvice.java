package com.osg.ai;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 扩容建议
 */
@Data
public class ScalingAdvice implements Serializable {

    private static final long serialVersionUID = 1L;

    private long adviceTime;
    private String resourceType;
    private int currentReplicas;
    private int recommendedReplicas;
    private String action;
    private String reason;
    private double urgency;
    private List<ScalingFactor> factors;
    private double confidence;

    @Data
    public static class ScalingFactor implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private double currentValue;
        private double threshold;
        private double weight;
        private String impact;
    }
}
