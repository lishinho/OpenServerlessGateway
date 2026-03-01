package com.osg.replay;

import lombok.Data;

/**
 * 回放结果
 */
@Data
public class ReplayResult {

    private String traceId;
    private long originalDuration;
    private long replayDuration;
    private boolean success;
    private String errorMessage;
    private Object result;
    private Object originalResult;

    public static ReplayResult error(String errorMessage) {
        ReplayResult result = new ReplayResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
