package com.osg.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发控制管理器
 * 支持接口级线程池、并发数限制、队列策略
 */
public class ConcurrencyManager {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyManager.class);

    private Map<String, ThreadPoolExecutor> threadPoolMap = new ConcurrentHashMap<>();
    private Map<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();
    private int defaultCorePoolSize;
    private int defaultMaxPoolSize;
    private int defaultQueueCapacity;
    private int defaultConcurrencyLimit;

    public ConcurrencyManager(int defaultCorePoolSize, int defaultMaxPoolSize, 
                              int defaultQueueCapacity, int defaultConcurrencyLimit) {
        this.defaultCorePoolSize = defaultCorePoolSize;
        this.defaultMaxPoolSize = defaultMaxPoolSize;
        this.defaultQueueCapacity = defaultQueueCapacity;
        this.defaultConcurrencyLimit = defaultConcurrencyLimit;
    }

    /**
     * 获取或创建线程池
     * @param apiId API标识
     * @return 线程池
     */
    public ThreadPoolExecutor getOrCreateThreadPool(String apiId) {
        return threadPoolMap.computeIfAbsent(apiId, this::createThreadPool);
    }

    /**
     * 创建线程池
     */
    private ThreadPoolExecutor createThreadPool(String apiId) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            defaultCorePoolSize,
            defaultMaxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(defaultQueueCapacity),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "osg-" + apiId + "-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        log.info("创建线程池: apiId={}, core={}, max={}, queue={}", 
            apiId, defaultCorePoolSize, defaultMaxPoolSize, defaultQueueCapacity);
        return executor;
    }

    /**
     * 获取或创建信号量
     * @param apiId API标识
     * @return 信号量
     */
    public Semaphore getOrCreateSemaphore(String apiId) {
        return semaphoreMap.computeIfAbsent(apiId, k -> new Semaphore(defaultConcurrencyLimit));
    }

    /**
     * 尝试获取许可
     * @param apiId API标识
     * @param timeout 超时时间（毫秒）
     * @return 是否获取成功
     */
    public boolean tryAcquire(String apiId, long timeout) {
        try {
            Semaphore semaphore = getOrCreateSemaphore(apiId);
            boolean acquired = semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS);
            
            if (!acquired) {
                log.warn("并发限制: apiId={}, available={}", apiId, semaphore.availablePermits());
            }
            
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放许可
     * @param apiId API标识
     */
    public void release(String apiId) {
        Semaphore semaphore = semaphoreMap.get(apiId);
        if (semaphore != null) {
            semaphore.release();
        }
    }

    /**
     * 提交任务
     * @param apiId API标识
     * @param task 任务
     * @return Future
     */
    public <T> Future<T> submit(String apiId, Callable<T> task) {
        ThreadPoolExecutor executor = getOrCreateThreadPool(apiId);
        return executor.submit(task);
    }

    /**
     * 提交任务
     * @param apiId API标识
     * @param task 任务
     */
    public void execute(String apiId, Runnable task) {
        ThreadPoolExecutor executor = getOrCreateThreadPool(apiId);
        executor.execute(task);
    }

    /**
     * 获取线程池状态
     * @param apiId API标识
     * @return 状态信息
     */
    public Map<String, Object> getPoolStatus(String apiId) {
        ThreadPoolExecutor executor = threadPoolMap.get(apiId);
        if (executor == null) {
            return null;
        }

        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("corePoolSize", executor.getCorePoolSize());
        status.put("maxPoolSize", executor.getMaximumPoolSize());
        status.put("activeCount", executor.getActiveCount());
        status.put("poolSize", executor.getPoolSize());
        status.put("queueSize", executor.getQueue().size());
        status.put("completedTaskCount", executor.getCompletedTaskCount());
        return status;
    }

    /**
     * 销毁线程池
     * @param apiId API标识
     */
    public void destroyThreadPool(String apiId) {
        ThreadPoolExecutor executor = threadPoolMap.remove(apiId);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("销毁线程池: apiId={}", apiId);
        }
        
        semaphoreMap.remove(apiId);
    }

    /**
     * 销毁所有线程池
     */
    public void destroy() {
        for (String apiId : threadPoolMap.keySet()) {
            destroyThreadPool(apiId);
        }
        log.info("销毁所有线程池完成");
    }
}
