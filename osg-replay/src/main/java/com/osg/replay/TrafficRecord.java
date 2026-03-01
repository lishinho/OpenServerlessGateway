package com.osg.replay;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * 流量录制记录
 */
@Data
public class TrafficRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String traceId;
    private String requestPath;
    private String httpMethod;
    private String clientIp;
    private String userId;
    private Map<String, String> headers;
    private String requestBody;
    private String responseBody;
    private long duration;
    private boolean success;
    private long timestamp;
}
