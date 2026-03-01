package com.osg.core.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志监控引擎
 * 统一日志格式、监控指标采集、告警触发
 */
@Component
public class MonitorEngine {

    private static final Logger log = LoggerFactory.getLogger(MonitorEngine.class);

    private final MeterRegistry meterRegistry;
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    private Counter requestCounter;
    private Counter errorCounter;
    private Counter rateLimitCounter;
    private Counter authFailCounter;
    private Timer requestTimer;

    public MonitorEngine(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    private void initMetrics() {
        requestCounter = Counter.builder("osg_request_total")
            .description("Total requests")
            .register(meterRegistry);

        errorCounter = Counter.builder("osg_error_total")
            .description("Total errors")
            .register(meterRegistry);

        rateLimitCounter = Counter.builder("osg_rate_limit_total")
            .description("Rate limited requests")
            .register(meterRegistry);

        authFailCounter = Counter.builder("osg_auth_fail_total")
            .description("Authentication failed requests")
            .register(meterRegistry);

        requestTimer = Timer.builder("osg_request_duration")
            .description("Request duration")
            .register(meterRegistry);
    }

    /**
     * 记录请求开始
     * @param traceId 追踪ID
     * @param path 请求路径
     * @param method HTTP方法
     */
    public void recordRequestStart(String traceId, String path, String method) {
        requestCount.incrementAndGet();
        requestCounter.increment();
        log.info("[REQUEST_START] traceId={}, method={}, path={}", traceId, method, path);
    }

    /**
     * 记录请求结束
     * @param traceId 追踪ID
     * @param path 请求路径
     * @param method HTTP方法
     * @param duration 耗时（毫秒）
     * @param success 是否成功
     */
    public void recordRequestEnd(String traceId, String path, String method, long duration, boolean success) {
        requestTimer.record(duration, TimeUnit.MILLISECONDS);
        if (!success) {
            errorCount.incrementAndGet();
            errorCounter.increment();
        }
        log.info("[REQUEST_END] traceId={}, method={}, path={}, duration={}ms, success={}", 
            traceId, method, path, duration, success);
    }

    /**
     * 记录限流事件
     * @param traceId 追踪ID
     * @param path 请求路径
     * @param dimension 限流维度
     */
    public void recordRateLimit(String traceId, String path, String dimension) {
        rateLimitCounter.increment();
        log.warn("[RATE_LIMIT] traceId={}, path={}, dimension={}", traceId, path, dimension);
    }

    /**
     * 记录认证失败
     * @param traceId 追踪ID
     * @param path 请求路径
     * @param reason 失败原因
     */
    public void recordAuthFail(String traceId, String path, String reason) {
        authFailCounter.increment();
        log.warn("[AUTH_FAIL] traceId={}, path={}, reason={}", traceId, path, reason);
    }

    /**
     * 记录灰度路由
     * @param traceId 追踪ID
     * @param path 请求路径
     * @param version 目标版本
     */
    public void recordGrayRoute(String traceId, String path, String version) {
        log.info("[GRAY_ROUTE] traceId={}, path={}, version={}", traceId, path, version);
    }

    /**
     * 记录重试
     * @param traceId 追踪ID
     * @param path 请求路径
     * @param retryCount 重试次数
     */
    public void recordRetry(String traceId, String path, int retryCount) {
        log.info("[RETRY] traceId={}, path={}, retryCount={}", traceId, path, retryCount);
    }

    /**
     * 记录降级
     * @param traceId 追踪ID
     * @param path 请求路径
     * @param strategy 降级策略
     */
    public void recordDegrade(String traceId, String path, String strategy) {
        log.warn("[DEGRADE] traceId={}, path={}, strategy={}", traceId, path, strategy);
    }

    /**
     * 获取请求总数
     * @return 请求总数
     */
    public long getRequestCount() {
        return requestCount.get();
    }

    /**
     * 获取错误总数
     * @return 错误总数
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * 获取错误率
     * @return 错误率
     */
    public double getErrorRate() {
        long total = requestCount.get();
        if (total == 0) {
            return 0;
        }
        return (double) errorCount.get() / total;
    }
}
