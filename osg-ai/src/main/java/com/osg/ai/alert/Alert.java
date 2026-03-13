package com.osg.ai.alert;

import com.osg.ai.AnomalyResult;
import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 告警对象
 */
@Data
public class Alert implements Serializable {

    private static final long serialVersionUID = 1L;

    private String alertId;
    private long alertTime;
    private String anomalyType;
    private String severity;
    private String description;
    private double anomalyScore;
    private List<AnomalyResult.AnomalyFeature> features;
    private Map<String, Object> context;
    private String status;
    private int aggregatedCount;
}
