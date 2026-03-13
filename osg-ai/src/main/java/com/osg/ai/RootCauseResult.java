package com.osg.ai;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 根因分析结果
 */
@Data
public class RootCauseResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String incidentId;
    private long analysisTime;
    private List<RootCause> rootCauses;
    private String summary;
    private List<String> suggestedActions;
    private double confidence;

    @Data
    public static class RootCause implements Serializable {
        private static final long serialVersionUID = 1L;
        private String component;
        private String cause;
        private double probability;
        private List<String> evidence;
        private String impact;
    }
}
