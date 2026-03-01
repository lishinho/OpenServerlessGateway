package com.osg.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.osg.api.spi.Authenticator;
import com.osg.common.enums.AuthType;
import com.osg.common.exception.AuthenticationException;
import com.osg.common.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT认证器
 * 支持RS256/HS256签名算法
 */
public class JwtAuthenticator implements Authenticator {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticator.class);

    private static final String NAME = "JwtAuthenticator";
    private static final String TYPE = AuthType.JWT.getCode();

    private String secret;
    private String issuer;
    private long expireTime;
    private Algorithm algorithm;
    private JWTVerifier verifier;
    private StringRedisTemplate redisTemplate;

    public JwtAuthenticator(String secret, String issuer, long expireTime) {
        this.secret = secret;
        this.issuer = issuer;
        this.expireTime = expireTime;
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm)
            .withIssuer(issuer)
            .build();
    }

    @Override
    public boolean authenticate(GatewayContext context, String token) {
        if (token == null || token.isEmpty()) {
            throw new AuthenticationException("Token不能为空");
        }

        try {
            String jwtToken = token;
            if (token.startsWith("Bearer ")) {
                jwtToken = token.substring(7);
            }

            DecodedJWT jwt = verifier.verify(jwtToken);
            
            String userId = jwt.getClaim("userId").asString();
            String userName = jwt.getClaim("userName").asString();
            String tenantId = jwt.getClaim("tenantId").asString();
            String[] roles = jwt.getClaim("roles").asArray(String.class);
            String[] permissions = jwt.getClaim("permissions").asArray(String.class);

            context.setUserId(userId);
            context.setUserName(userName);
            context.setTenantId(tenantId);
            context.setRoles(roles);
            context.setPermissions(permissions);

            log.debug("JWT认证成功: userId={}, userName={}", userId, userName);
            return true;
        } catch (JWTVerificationException e) {
            log.warn("JWT认证失败: {}", e.getMessage());
            throw new AuthenticationException("JWT验证失败: " + e.getMessage());
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
    public String refreshToken(String token) {
        try {
            String jwtToken = token;
            if (token.startsWith("Bearer ")) {
                jwtToken = token.substring(7);
            }

            DecodedJWT jwt = verifier.verify(jwtToken);
            String[] roles = jwt.getClaim("roles").asArray(String.class);
            String[] permissions = jwt.getClaim("permissions").asArray(String.class);
            
            return JWT.create()
                .withIssuer(issuer)
                .withSubject(jwt.getSubject())
                .withClaim("userId", jwt.getClaim("userId").asString())
                .withClaim("userName", jwt.getClaim("userName").asString())
                .withClaim("tenantId", jwt.getClaim("tenantId").asString())
                .withClaim("roles", roles != null ? Arrays.asList(roles) : null)
                .withClaim("permissions", permissions != null ? Arrays.asList(permissions) : null)
                .withExpiresAt(new Date(System.currentTimeMillis() + expireTime * 1000))
                .sign(algorithm);
        } catch (JWTVerificationException e) {
            throw new AuthenticationException("Token刷新失败: " + e.getMessage());
        }
    }

    @Override
    public boolean validateToken(String token) {
        try {
            String jwtToken = token;
            if (token.startsWith("Bearer ")) {
                jwtToken = token.substring(7);
            }

            if (redisTemplate != null) {
                String blackKey = "osg:auth:blacklist:" + jwtToken;
                if (Boolean.TRUE.equals(redisTemplate.hasKey(blackKey))) {
                    return false;
                }
            }

            verifier.verify(jwtToken);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    /**
     * 生成Token
     * @param userId 用户ID
     * @param userName 用户名
     * @param tenantId 租户ID
     * @param roles 角色
     * @param permissions 权限
     * @return JWT Token
     */
    public String generateToken(String userId, String userName, String tenantId, 
                                String[] roles, String[] permissions) {
        return JWT.create()
            .withIssuer(issuer)
            .withSubject(userId)
            .withClaim("userId", userId)
            .withClaim("userName", userName)
            .withClaim("tenantId", tenantId)
            .withClaim("roles", roles != null ? Arrays.asList(roles) : null)
            .withClaim("permissions", permissions != null ? Arrays.asList(permissions) : null)
            .withExpiresAt(new Date(System.currentTimeMillis() + expireTime * 1000))
            .sign(algorithm);
    }

    /**
     * 注销Token（加入黑名单）
     * @param token Token
     */
    public void invalidateToken(String token) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String jwtToken = token;
            if (token.startsWith("Bearer ")) {
                jwtToken = token.substring(7);
            }

            DecodedJWT jwt = verifier.verify(jwtToken);
            Date expiresAt = jwt.getExpiresAt();
            long ttl = (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
            
            if (ttl > 0) {
                String blackKey = "osg:auth:blacklist:" + jwtToken;
                redisTemplate.opsForValue().set(blackKey, "1", ttl, TimeUnit.SECONDS);
            }
        } catch (JWTVerificationException e) {
            log.warn("注销Token失败: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        log.info("销毁JWT认证器");
    }

    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
