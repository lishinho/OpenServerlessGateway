package com.osg.protocol;

import com.osg.api.spi.ProtocolAdapter;
import com.osg.common.enums.ProtocolType;
import com.osg.common.exception.ProtocolConvertException;
import com.osg.common.model.ApiMetadata;
import com.osg.common.model.GatewayContext;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * gRPC协议适配器
 */
public class GrpcProtocolAdapter implements ProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(GrpcProtocolAdapter.class);

    private static final String NAME = "GrpcProtocolAdapter";
    private static final String TYPE = ProtocolType.GRPC.getCode();

    private final Map<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();
    private int defaultTimeout = 3000;

    @Override
    public void init(Map<String, Object> config) {
        if (config.containsKey("defaultTimeout")) {
            this.defaultTimeout = (Integer) config.get("defaultTimeout");
        }
        log.info("初始化gRPC协议适配器: defaultTimeout={}", defaultTimeout);
    }

    @Override
    public Object invoke(GatewayContext context, ApiMetadata apiMetadata, Map<String, Object> params) {
        String serviceName = apiMetadata.getServiceName();
        String methodName = apiMetadata.getMethodName();
        int timeout = apiMetadata.getTimeout() > 0 ? apiMetadata.getTimeout() : defaultTimeout;

        try {
            ManagedChannel channel = getChannel(serviceName);
            
            log.debug("gRPC调用: service={}, method={}", serviceName, methodName);
            
            return null;
        } catch (Exception e) {
            log.error("gRPC调用失败: service={}, method={}, error={}", 
                serviceName, methodName, e.getMessage(), e);
            throw new ProtocolConvertException("gRPC调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取gRPC通道
     */
    private ManagedChannel getChannel(String serviceName) {
        return channelCache.computeIfAbsent(serviceName, name -> {
            String[] parts = name.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9090;
            
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
            
            log.info("创建gRPC通道: host={}, port={}", host, port);
            return channel;
        });
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getProtocolType() {
        return TYPE;
    }

    @Override
    public String[] discoverService(String serviceName) {
        return new String[]{serviceName};
    }

    @Override
    public void destroy() {
        log.info("销毁gRPC协议适配器");
        for (ManagedChannel channel : channelCache.values()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("关闭gRPC通道失败: {}", e.getMessage());
            }
        }
        channelCache.clear();
    }
}
