package com.osg.core.context;

import com.osg.common.constant.GatewayConstants;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * 上下文管理引擎
 * 全链路上下文透传、元数据共享
 */
@Component
public class ContextEngine {

    private static final Logger log = LoggerFactory.getLogger(ContextEngine.class);

    private static final ThreadLocal<GatewayContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 创建上下文
     * @param request HTTP请求
     * @return 网关上下文
     */
    public GatewayContext createContext(HttpServletRequest request) {
        GatewayContext context = new GatewayContext();
        
        context.setTraceId(generateTraceId(request));
        context.setClientIp(getClientIp(request));
        context.setUserAgent(request.getHeader("User-Agent"));
        context.setRequestPath(request.getRequestURI());
        context.setHttpMethod(request.getMethod());
        
        extractAuthInfo(context, request);
        
        CONTEXT_HOLDER.set(context);
        return context;
    }

    /**
     * 获取当前上下文
     * @return 网关上下文
     */
    public GatewayContext getCurrentContext() {
        GatewayContext context = CONTEXT_HOLDER.get();
        if (context == null) {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                return createContext(request);
            }
        }
        return context;
    }

    /**
     * 设置上下文
     * @param context 网关上下文
     */
    public void setContext(GatewayContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 清除上下文
     */
    public void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 获取当前HTTP请求
     * @return HTTP请求
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 生成追踪ID
     * @param request HTTP请求
     * @return 追踪ID
     */
    private String generateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(GatewayConstants.CONTEXT_KEY_TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }

    /**
     * 获取客户端IP
     * @param request HTTP请求
     * @return 客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 提取认证信息
     * @param context 网关上下文
     * @param request HTTP请求
     */
    private void extractAuthInfo(GatewayContext context, HttpServletRequest request) {
        String userId = request.getHeader(GatewayConstants.CONTEXT_KEY_USER_ID);
        String userName = request.getHeader(GatewayConstants.CONTEXT_KEY_USER_NAME);
        String tenantId = request.getHeader(GatewayConstants.CONTEXT_KEY_TENANT_ID);
        String roles = request.getHeader(GatewayConstants.CONTEXT_KEY_USER_ROLES);
        String permissions = request.getHeader(GatewayConstants.CONTEXT_KEY_USER_PERMISSIONS);

        context.setUserId(userId);
        context.setUserName(userName);
        context.setTenantId(tenantId);
        
        if (roles != null && !roles.isEmpty()) {
            context.setRoles(roles.split(","));
        }
        if (permissions != null && !permissions.isEmpty()) {
            context.setPermissions(permissions.split(","));
        }
    }

    /**
     * 注入上下文到请求头
     * @param context 网关上下文
     * @param headers 请求头映射
     */
    public void injectContextToHeaders(GatewayContext context, java.util.Map<String, String> headers) {
        if (context == null) {
            return;
        }
        
        if (context.getTraceId() != null) {
            headers.put(GatewayConstants.CONTEXT_KEY_TRACE_ID, context.getTraceId());
        }
        if (context.getUserId() != null) {
            headers.put(GatewayConstants.CONTEXT_KEY_USER_ID, context.getUserId());
        }
        if (context.getUserName() != null) {
            headers.put(GatewayConstants.CONTEXT_KEY_USER_NAME, context.getUserName());
        }
        if (context.getTenantId() != null) {
            headers.put(GatewayConstants.CONTEXT_KEY_TENANT_ID, context.getTenantId());
        }
        if (context.getRoles() != null && context.getRoles().length > 0) {
            headers.put(GatewayConstants.CONTEXT_KEY_USER_ROLES, String.join(",", context.getRoles()));
        }
        if (context.getPermissions() != null && context.getPermissions().length > 0) {
            headers.put(GatewayConstants.CONTEXT_KEY_USER_PERMISSIONS, String.join(",", context.getPermissions()));
        }
    }
}
