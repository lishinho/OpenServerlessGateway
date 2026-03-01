package com.osg.gray;

import com.osg.api.spi.GrayStrategy;
import com.osg.common.enums.GrayStrategyType;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * IP白名单灰度策略
 */
public class IpWhitelistGrayStrategy implements GrayStrategy {

    private static final Logger log = LoggerFactory.getLogger(IpWhitelistGrayStrategy.class);

    private static final String NAME = "IpWhitelistGrayStrategy";
    private static final String TYPE = GrayStrategyType.IP_WHITELIST.getCode();

    @Override
    public boolean match(GatewayContext context, String ruleConfig) {
        String clientIp = context.getClientIp();
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }

        Set<String> whitelist = parseWhitelist(ruleConfig);
        boolean matched = whitelist.contains(clientIp);
        
        log.debug("IP白名单灰度匹配: ip={}, matched={}", clientIp, matched);
        return matched;
    }

    private Set<String> parseWhitelist(String ruleConfig) {
        if (ruleConfig == null || ruleConfig.isEmpty()) {
            return new HashSet<>();
        }
        String[] ips = ruleConfig.split(",");
        return new HashSet<>(Arrays.asList(ips));
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void destroy() {
        log.info("销毁IP白名单灰度策略");
    }
}
