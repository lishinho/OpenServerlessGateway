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
 * 用户白名单灰度策略
 */
public class UserWhitelistGrayStrategy implements GrayStrategy {

    private static final Logger log = LoggerFactory.getLogger(UserWhitelistGrayStrategy.class);

    private static final String NAME = "UserWhitelistGrayStrategy";
    private static final String TYPE = GrayStrategyType.USER_WHITELIST.getCode();

    @Override
    public boolean match(GatewayContext context, String ruleConfig) {
        String userId = context.getUserId();
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        Set<String> whitelist = parseWhitelist(ruleConfig);
        boolean matched = whitelist.contains(userId);
        
        log.debug("用户白名单灰度匹配: userId={}, matched={}", userId, matched);
        return matched;
    }

    /**
     * 解析白名单配置
     */
    private Set<String> parseWhitelist(String ruleConfig) {
        if (ruleConfig == null || ruleConfig.isEmpty()) {
            return new HashSet<>();
        }
        String[] users = ruleConfig.split(",");
        return new HashSet<>(Arrays.asList(users));
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
        log.info("销毁用户白名单灰度策略");
    }
}
