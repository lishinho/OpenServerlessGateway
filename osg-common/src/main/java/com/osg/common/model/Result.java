package com.osg.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 统一响应结果
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private String message;
    private T data;
    private long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success(T data) {
        return new Result<>("0", "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>("0", "success", null);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>("-1", message, null);
    }

    public boolean isSuccess() {
        return "0".equals(this.code);
    }
}
