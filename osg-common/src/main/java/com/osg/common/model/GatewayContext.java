package com.osg.common.model;

import lombok.Data;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 网关上下文信息
 */
@Data
public class GatewayContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String traceId;
    private String userId;
    private String userName;
    private String tenantId;
    private String[] roles;
    private String[] permissions;
    private String clientIp;
    private String userAgent;
    private String requestPath;
    private String httpMethod;
    private long requestTime;
    private Map<String, Object> attributes;

    public GatewayContext() {
        this.requestTime = System.currentTimeMillis();
        this.attributes = new HashMap<>();
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    public boolean hasRole(String role) {
        if (roles == null) {
            return false;
        }
        for (String r : roles) {
            if (r.equals(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPermission(String permission) {
        if (permissions == null) {
            return false;
        }
        for (String p : permissions) {
            if (p.equals(permission) || "*".equals(p)) {
                return true;
            }
        }
        return false;
    }
}
