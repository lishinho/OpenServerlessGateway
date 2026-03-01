package com.osg.auth;

import com.osg.api.spi.Authorizer;
import com.osg.common.enums.AuthType;
import com.osg.common.exception.AuthorizationException;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * RBAC权限校验器
 * 基于角色的访问控制
 */
public class RbacAuthorizer implements Authorizer {

    private static final Logger log = LoggerFactory.getLogger(RbacAuthorizer.class);

    private static final String NAME = "RbacAuthorizer";
    private static final String TYPE = "rbac";

    private Map<String, Set<String>> rolePermissions = new HashMap<>();
    private Map<String, String[]> apiPermissions = new HashMap<>();

    @Override
    public boolean authorize(GatewayContext context, String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }

        String[] permissions = context.getPermissions();
        if (permissions == null || permissions.length == 0) {
            throw new AuthorizationException("用户无任何权限");
        }

        for (String p : permissions) {
            if ("*".equals(p) || p.equals(permission)) {
                log.debug("RBAC权限校验通过: userId={}, permission={}", context.getUserId(), permission);
                return true;
            }
        }

        log.warn("RBAC权限校验失败: userId={}, permission={}", context.getUserId(), permission);
        throw new AuthorizationException("权限不足: " + permission);
    }

    @Override
    public boolean hasRole(GatewayContext context, String role) {
        if (role == null || role.isEmpty()) {
            return true;
        }

        String[] roles = context.getRoles();
        if (roles == null || roles.length == 0) {
            return false;
        }

        for (String r : roles) {
            if (r.equals(role)) {
                log.debug("RBAC角色校验通过: userId={}, role={}", context.getUserId(), role);
                return true;
            }
        }

        log.warn("RBAC角色校验失败: userId={}, role={}", context.getUserId(), role);
        return false;
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
     * 注册角色权限
     * @param role 角色
     * @param permissions 权限列表
     */
    public void registerRolePermissions(String role, Set<String> permissions) {
        rolePermissions.put(role, new HashSet<>(permissions));
        log.info("注册角色权限: role={}, permissions={}", role, permissions);
    }

    /**
     * 注册API权限
     * @param apiId API标识
     * @param permissions 所需权限
     */
    public void registerApiPermissions(String apiId, String[] permissions) {
        apiPermissions.put(apiId, permissions);
        log.info("注册API权限: apiId={}, permissions={}", apiId, Arrays.toString(permissions));
    }

    /**
     * 获取角色的所有权限
     * @param roles 角色列表
     * @return 权限集合
     */
    public Set<String> getPermissionsByRoles(String[] roles) {
        Set<String> allPermissions = new HashSet<>();
        if (roles != null) {
            for (String role : roles) {
                Set<String> perms = rolePermissions.get(role);
                if (perms != null) {
                    allPermissions.addAll(perms);
                }
            }
        }
        return allPermissions;
    }

    @Override
    public void destroy() {
        log.info("销毁RBAC权限校验器");
        rolePermissions.clear();
        apiPermissions.clear();
    }
}
