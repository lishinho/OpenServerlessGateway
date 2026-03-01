package com.osg.gray;

import com.osg.api.spi.GrayStrategy;
import com.osg.common.enums.GrayStrategyType;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;

/**
 * 权重灰度策略
 */
public class WeightGrayStrategy implements GrayStrategy {

    private static final Logger log = LoggerFactory.getLogger(WeightGrayStrategy.class);

    private static final String NAME = "WeightGrayStrategy";
    private static final String TYPE = GrayStrategyType.WEIGHT.getCode();

    private final Random random = new Random();

    @Override
    public boolean match(GatewayContext context, String ruleConfig) {
        int weight = parseWeight(ruleConfig);
        int value = random.nextInt(100);
        boolean matched = value < weight;
        
        log.debug("权重灰度匹配: weight={}, value={}, matched={}", weight, value, matched);
        return matched;
    }

    /**
     * 解析权重配置
     */
    private int parseWeight(String ruleConfig) {
        if (ruleConfig == null || ruleConfig.isEmpty()) {
            return 10;
        }
        try {
            return Integer.parseInt(ruleConfig);
        } catch (NumberFormatException e) {
            return 10;
        }
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
        log.info("销毁权重灰度策略");
    }
}
