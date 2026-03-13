package com.osg.ai;

import lombok.Data;
import java.io.Serializable;

/**
 * 流量数据
 */
@Data
public class TrafficData implements Serializable {

    private static final long serialVersionUID = 1L;

    private long timestamp;
    private String apiId;
    private long requestCount;
    private double avgLatency;
    private long errorCount;
    private double errorRate;
    private int concurrentRequests;
    private double cpuUsage;
    private double memoryUsage;
}
