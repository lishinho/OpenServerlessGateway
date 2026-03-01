package com.osg.core.plugin;

import com.osg.api.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件管理引擎
 * 基于SPI机制实现插件的加载、初始化、销毁等生命周期管理
 */
@Component
public class PluginEngine {

    private static final Logger log = LoggerFactory.getLogger(PluginEngine.class);

    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final Map<String, Authenticator> authenticators = new ConcurrentHashMap<>();
    private final Map<String, Authorizer> authorizers = new ConcurrentHashMap<>();
    private final Map<String, ProtocolAdapter> protocolAdapters = new ConcurrentHashMap<>();
    private final Map<String, GrayStrategy> grayStrategies = new ConcurrentHashMap<>();

    private final ServiceLoader<RateLimiter> rateLimiterLoader = ServiceLoader.load(RateLimiter.class);
    private final ServiceLoader<Authenticator> authenticatorLoader = ServiceLoader.load(Authenticator.class);
    private final ServiceLoader<Authorizer> authorizerLoader = ServiceLoader.load(Authorizer.class);
    private final ServiceLoader<ProtocolAdapter> protocolAdapterLoader = ServiceLoader.load(ProtocolAdapter.class);
    private final ServiceLoader<GrayStrategy> grayStrategyLoader = ServiceLoader.load(GrayStrategy.class);

    @PostConstruct
    public void init() {
        log.info("初始化插件管理引擎...");
        loadPlugins();
        log.info("插件管理引擎初始化完成");
    }

    /**
     * 加载所有插件
     */
    private void loadPlugins() {
        loadRateLimiters();
        loadAuthenticators();
        loadAuthorizers();
        loadProtocolAdapters();
        loadGrayStrategies();
    }

    private void loadRateLimiters() {
        for (RateLimiter limiter : rateLimiterLoader) {
            rateLimiters.put(limiter.getType(), limiter);
            log.info("加载限流器插件: {} - {}", limiter.getType(), limiter.getName());
        }
    }

    private void loadAuthenticators() {
        for (Authenticator authenticator : authenticatorLoader) {
            authenticators.put(authenticator.getType(), authenticator);
            log.info("加载认证器插件: {} - {}", authenticator.getType(), authenticator.getName());
        }
    }

    private void loadAuthorizers() {
        for (Authorizer authorizer : authorizerLoader) {
            authorizers.put(authorizer.getType(), authorizer);
            log.info("加载权限校验器插件: {} - {}", authorizer.getType(), authorizer.getName());
        }
    }

    private void loadProtocolAdapters() {
        for (ProtocolAdapter adapter : protocolAdapterLoader) {
            protocolAdapters.put(adapter.getProtocolType(), adapter);
            log.info("加载协议适配器插件: {} - {}", adapter.getProtocolType(), adapter.getName());
        }
    }

    private void loadGrayStrategies() {
        for (GrayStrategy strategy : grayStrategyLoader) {
            grayStrategies.put(strategy.getType(), strategy);
            log.info("加载灰度策略插件: {} - {}", strategy.getType(), strategy.getName());
        }
    }

    /**
     * 获取限流器
     * @param type 类型
     * @return 限流器
     */
    public RateLimiter getRateLimiter(String type) {
        return rateLimiters.get(type);
    }

    /**
     * 获取认证器
     * @param type 类型
     * @return 认证器
     */
    public Authenticator getAuthenticator(String type) {
        return authenticators.get(type);
    }

    /**
     * 获取权限校验器
     * @param type 类型
     * @return 权限校验器
     */
    public Authorizer getAuthorizer(String type) {
        return authorizers.get(type);
    }

    /**
     * 获取协议适配器
     * @param protocolType 协议类型
     * @return 协议适配器
     */
    public ProtocolAdapter getProtocolAdapter(String protocolType) {
        return protocolAdapters.get(protocolType);
    }

    /**
     * 获取灰度策略
     * @param type 类型
     * @return 灰度策略
     */
    public GrayStrategy getGrayStrategy(String type) {
        return grayStrategies.get(type);
    }

    /**
     * 注册限流器
     * @param limiter 限流器
     */
    public void registerRateLimiter(RateLimiter limiter) {
        rateLimiters.put(limiter.getType(), limiter);
        log.info("注册限流器: {} - {}", limiter.getType(), limiter.getName());
    }

    /**
     * 注册认证器
     * @param authenticator 认证器
     */
    public void registerAuthenticator(Authenticator authenticator) {
        authenticators.put(authenticator.getType(), authenticator);
        log.info("注册认证器: {} - {}", authenticator.getType(), authenticator.getName());
    }

    /**
     * 注册协议适配器
     * @param adapter 协议适配器
     */
    public void registerProtocolAdapter(ProtocolAdapter adapter) {
        protocolAdapters.put(adapter.getProtocolType(), adapter);
        log.info("注册协议适配器: {} - {}", adapter.getProtocolType(), adapter.getName());
    }

    /**
     * 获取所有限流器类型
     * @return 类型列表
     */
    public List<String> getRateLimiterTypes() {
        return new ArrayList<>(rateLimiters.keySet());
    }

    /**
     * 获取所有认证器类型
     * @return 类型列表
     */
    public List<String> getAuthenticatorTypes() {
        return new ArrayList<>(authenticators.keySet());
    }

    /**
     * 获取所有协议类型
     * @return 类型列表
     */
    public List<String> getProtocolTypes() {
        return new ArrayList<>(protocolAdapters.keySet());
    }

    /**
     * 热加载插件
     */
    public void hotReload() {
        log.info("开始热加载插件...");
        rateLimiterLoader.reload();
        authenticatorLoader.reload();
        authorizerLoader.reload();
        protocolAdapterLoader.reload();
        grayStrategyLoader.reload();
        loadPlugins();
        log.info("插件热加载完成");
    }

    @PreDestroy
    public void destroy() {
        log.info("销毁插件管理引擎...");
        destroyPlugins(rateLimiters.values());
        destroyPlugins(authenticators.values());
        destroyPlugins(authorizers.values());
        destroyPlugins(protocolAdapters.values());
        destroyPlugins(grayStrategies.values());
        rateLimiters.clear();
        authenticators.clear();
        authorizers.clear();
        protocolAdapters.clear();
        grayStrategies.clear();
        log.info("插件管理引擎销毁完成");
    }

    private void destroyPlugins(Collection<?> plugins) {
        for (Object plugin : plugins) {
            try {
                if (plugin instanceof RateLimiter) {
                    ((RateLimiter) plugin).destroy();
                } else if (plugin instanceof Authenticator) {
                    ((Authenticator) plugin).destroy();
                } else if (plugin instanceof Authorizer) {
                    ((Authorizer) plugin).destroy();
                } else if (plugin instanceof ProtocolAdapter) {
                    ((ProtocolAdapter) plugin).destroy();
                } else if (plugin instanceof GrayStrategy) {
                    ((GrayStrategy) plugin).destroy();
                }
            } catch (Exception e) {
                log.error("销毁插件失败: {}", plugin.getClass().getName(), e);
            }
        }
    }
}
