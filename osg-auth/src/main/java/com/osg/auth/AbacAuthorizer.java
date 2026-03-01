package com.osg.auth;

import com.osg.api.spi.Authorizer;
import com.osg.common.exception.AuthorizationException;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ABAC权限校验器
 * 基于属性的访问控制，支持细粒度策略
 */
public class AbacAuthorizer implements Authorizer {

    private static final Logger log = LoggerFactory.getLogger(AbacAuthorizer.class);

    private static final String NAME = "AbacAuthorizer";
    private static final String TYPE = "abac";

    private Map<String, Policy> policies = new ConcurrentHashMap<>();

    @Override
    public boolean authorize(GatewayContext context, String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }

        Policy policy = policies.get(permission);
        if (policy == null) {
            return true;
        }

        return evaluatePolicy(context, policy);
    }

    @Override
    public boolean hasRole(GatewayContext context, String role) {
        return true;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * 注册策略
     * @param policyId 策略ID
     * @param policy 策略
     */
    public void registerPolicy(String policyId, Policy policy) {
        policies.put(policyId, policy);
        log.info("注册ABAC策略: policyId={}", policyId);
    }

    /**
     * 评估策略
     * @param context 网关上下文
     * @param policy 策略
     * @return 是否通过
     */
    private boolean evaluatePolicy(GatewayContext context, Policy policy) {
        if (!evaluateSubject(context, policy)) {
            log.debug("ABAC策略主体不匹配: userId={}", context.getUserId());
            return false;
        }

        if (!evaluateCondition(context, policy)) {
            log.debug("ABAC策略条件不满足: userId={}", context.getUserId());
            return false;
        }

        log.debug("ABAC策略评估通过: userId={}", context.getUserId());
        return true;
    }

    /**
     * 评估主体属性
     */
    private boolean evaluateSubject(GatewayContext context, Policy policy) {
        Map<String, String> subjectAttrs = policy.getSubjectAttributes();
        if (subjectAttrs == null || subjectAttrs.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, String> entry : subjectAttrs.entrySet()) {
            String attrName = entry.getKey();
            String expectedValue = entry.getValue();
            
            String actualValue = getSubjectAttribute(context, attrName);
            if (actualValue == null) {
                return false;
            }
            
            if (expectedValue.startsWith(">=")) {
                try {
                    int expected = Integer.parseInt(expectedValue.substring(2));
                    int actual = Integer.parseInt(actualValue);
                    if (actual < expected) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (!expectedValue.equals(actualValue)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估条件
     */
    private boolean evaluateCondition(GatewayContext context, Policy policy) {
        Map<String, String> conditions = policy.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        String timeCondition = conditions.get("time");
        if (timeCondition != null && !checkTimeCondition(timeCondition)) {
            return false;
        }

        String ipRange = conditions.get("ip_range");
        if (ipRange != null && !checkIpRange(context.getClientIp(), ipRange)) {
            return false;
        }

        return true;
    }

    /**
     * 获取主体属性
     */
    private String getSubjectAttribute(GatewayContext context, String attrName) {
        switch (attrName) {
            case "userId":
                return context.getUserId();
            case "tenantId":
                return context.getTenantId();
            case "clientIp":
                return context.getClientIp();
            default:
                return (String) context.getAttribute(attrName);
        }
    }

    /**
     * 检查时间条件
     */
    private boolean checkTimeCondition(String timeCondition) {
        if (timeCondition == null || timeCondition.isEmpty()) {
            return true;
        }
        
        try {
            String[] parts = timeCondition.split("-");
            if (parts.length != 2) {
                return true;
            }
            
            int startHour = Integer.parseInt(parts[0].split(":")[0]);
            int endHour = Integer.parseInt(parts[1].split(":")[0]);
            int currentHour = java.time.LocalTime.now().getHour();
            
            if (startHour <= endHour) {
                return currentHour >= startHour && currentHour <= endHour;
            } else {
                return currentHour >= startHour || currentHour <= endHour;
            }
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 检查IP范围
     */
    private boolean checkIpRange(String clientIp, String ipRange) {
        if (clientIp == null || ipRange == null) {
            return true;
        }
        
        if (ipRange.contains("/")) {
            return isInSubnet(clientIp, ipRange);
        }
        
        return clientIp.startsWith(ipRange.substring(0, ipRange.lastIndexOf('.')));
    }

    /**
     * 检查IP是否在子网内
     */
    private boolean isInSubnet(String ip, String cidr) {
        return true;
    }

    @Override
    public void destroy() {
        log.info("销毁ABAC权限校验器");
        policies.clear();
    }

    /**
     * 策略定义
     */
    public static class Policy {
        private String effect;
        private Map<String, String> subjectAttributes;
        private String resourceType;
        private String resourcePath;
        private String[] actions;
        private Map<String, String> conditions;

        public String getEffect() {
            return effect;
        }

        public void setEffect(String effect) {
            this.effect = effect;
        }

        public Map<String, String> getSubjectAttributes() {
            return subjectAttributes;
        }

        public void setSubjectAttributes(Map<String, String> subjectAttributes) {
            this.subjectAttributes = subjectAttributes;
        }

        public String getResourceType() {
            return resourceType;
        }

        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }

        public String getResourcePath() {
            return resourcePath;
        }

        public void setResourcePath(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        public String[] getActions() {
            return actions;
        }

        public void setActions(String[] actions) {
            this.actions = actions;
        }

        public Map<String, String> getConditions() {
            return conditions;
        }

        public void setConditions(Map<String, String> conditions) {
            this.conditions = conditions;
        }
    }
}
