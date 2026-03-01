# 鉴权架构设计文档

## 1. 鉴权架构设计

### 1.1 整体架构

OpenServerlessGateway 采用分层鉴权架构，通过处理链模式实现灵活的鉴权流程。整体架构如下：

```
┌─────────────────────────────────────────────────────────────┐
│                        鉴权处理链                            │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐  │
│  │Token解析│ -> │身份认证 │ -> │权限校验 │ -> │上下文构建│  │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘  │
├─────────────────────────────────────────────────────────────┤
│                     鉴权策略引擎                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐             │   │
│  │  │ RBAC    │  │ ABAC    │  │ 自定义  │（SPI扩展）   │   │
│  │  └─────────┘  └─────────┘  └─────────┘             │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                     会话管理器                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ JWT无状态   │  │ Redis有状态 │  │ 混合模式    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 鉴权处理链

鉴权处理链采用责任链模式，每个处理器专注于单一职责，便于扩展和维护。

#### 1.2.1 Token解析器（Token Parser）

**职责**：从请求中提取认证令牌

**支持类型**：
- Bearer Token（JWT格式）
- API Key
- Basic Auth
- OAuth2 Access Token

**处理流程**：
```
请求到达 -> 检查Authorization Header -> 提取Token类型 -> 解析Token内容
```

**配置示例**：
```yaml
auth:
  token:
    priority:
      - bearer
      - api_key
      - basic
    bearer:
      jwt_issuer: "https://auth.example.com"
      jwt_audience: "gateway"
    api_key:
      header_name: "X-API-Key"
      query_param: "api_key"
```

#### 1.2.2 身份认证器（Authenticator）

**职责**：验证用户身份，获取用户详细信息

**认证流程**：
```
Token验证 -> 用户信息查询 -> 身份确认 -> 缓存用户信息
```

**支持方式**：
- JWT自包含验证
- 数据库查询验证
- LDAP/AD集成
- OAuth2 Provider验证

**实现要点**：
```go
type Authenticator interface {
    Authenticate(ctx context.Context, token string) (*UserIdentity, error)
    GetPriority() int
}

type UserIdentity struct {
    ID          string
    Username    string
    Email       string
    Roles       []string
    Groups      []string
    Attributes  map[string]interface{}
    TenantID    string
    AuthTime    time.Time
}
```

#### 1.2.3 权限校验器（Authorizer）

**职责**：检查用户是否有权限访问目标资源

**校验流程**：
```
获取用户权限 -> 匹配资源权限 -> 条件判断 -> 返回决策结果
```

**决策策略**：
- 白名单优先
- 拒绝优先原则
- 默认拒绝策略

#### 1.2.4 上下文构建器（Context Builder）

**职责**：构建鉴权上下文，注入HTTP Header

**构建内容**：
- 用户身份信息
- 权限列表
- 租户信息
- 追踪信息
- 签名信息

### 1.3 鉴权策略引擎

策略引擎采用插件化设计，支持多种权限模型并存。

#### 1.3.1 策略引擎架构

```go
type PolicyEngine interface {
    Evaluate(ctx context.Context, request *AuthRequest) (*AuthDecision, error)
    AddPolicy(policy Policy) error
    RemovePolicy(policyID string) error
    GetPolicies() []Policy
}

type AuthRequest struct {
    Subject    *Subject
    Resource   *Resource
    Action     string
    Context    map[string]interface{}
}

type AuthDecision struct {
    Allowed    bool
    Reason     string
    Obligations map[string]string
}
```

#### 1.3.2 策略优先级

1. 显式拒绝策略（最高优先级）
2. 显式允许策略
3. RBAC策略
4. ABAC策略
5. 默认策略（最低优先级）

#### 1.3.3 SPI扩展机制

```go
type CustomAuthorizer interface {
    Name() string
    Initialize(config map[string]interface{}) error
    Authorize(ctx context.Context, req *AuthRequest) (*AuthDecision, error)
}
```

**扩展配置**：
```yaml
auth:
  custom_authorizers:
    - name: "ip_whitelist"
      enabled: true
      config:
        whitelist: ["10.0.0.0/8", "192.168.0.0/16"]
    - name: "rate_limiter"
      enabled: true
      config:
        requests_per_minute: 100
```

### 1.4 会话管理器

会话管理器支持多种会话模式，适应不同场景需求。

#### 1.4.1 会话模式对比

| 模式 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| JWT无状态 | 无需存储、易扩展、性能高 | 无法主动失效、Token较大 | 微服务、API网关 |
| Redis有状态 | 可控性强、支持踢出、Token小 | 依赖存储、性能略低 | 传统Web应用 |
| 混合模式 | 兼具两者优点 | 实现复杂 | 企业级应用 |

#### 1.4.2 会话配置

```yaml
session:
  type: hybrid
  jwt:
    secret: "${JWT_SECRET}"
    algorithm: RS256
    expiry: 3600
    refresh_expiry: 86400
    issuer: "gateway"
    audience: "api"
  redis:
    address: "redis://localhost:6379"
    db: 0
    prefix: "session:"
    ttl: 3600
  hybrid:
    jwt_expiry: 300
    redis_check: true
    blacklist_enabled: true
```

---

## 2. RBAC 权限模型设计

### 2.1 模型概述

RBAC（Role-Based Access Control，基于角色的访问控制）通过角色作为用户和权限之间的桥梁，简化权限管理。

### 2.2 核心概念

```
用户 ----> 角色 ----> 权限
```

**实体定义**：
- **用户**：系统的使用者
- **角色**：一组权限的集合
- **权限**：对资源的操作许可
- **资源**：被保护的对象（API、数据等）

### 2.3 数据模型

#### 2.3.1 用户角色关联

```json
{
  "user": {
    "id": "user123",
    "username": "zhang_san",
    "email": "zhangsan@example.com",
    "roles": ["admin", "developer"],
    "status": "active",
    "created_at": "2024-01-01T00:00:00Z"
  }
}
```

#### 2.3.2 角色定义

```json
{
  "roles": {
    "admin": {
      "name": "管理员",
      "description": "系统管理员角色，拥有所有权限",
      "permissions": ["*"],
      "inherits": [],
      "constraints": {
        "max_sessions": 5,
        "session_timeout": 7200
      }
    },
    "developer": {
      "name": "开发者",
      "description": "开发者角色，拥有API读写权限",
      "permissions": ["api:read", "api:write", "config:read"],
      "inherits": ["viewer"],
      "constraints": {
        "max_sessions": 3,
        "session_timeout": 3600
      }
    },
    "viewer": {
      "name": "查看者",
      "description": "只读角色，仅拥有查看权限",
      "permissions": ["api:read", "config:read"],
      "inherits": [],
      "constraints": {
        "max_sessions": 2,
        "session_timeout": 1800
      }
    }
  }
}
```

#### 2.3.3 API权限映射

```json
{
  "api_permissions": {
    "/api/v1/users": {
      "GET": ["user:read", "user:list"],
      "POST": ["user:create"],
      "PUT": ["user:update"],
      "DELETE": ["user:delete"]
    },
    "/api/v1/users/{id}": {
      "GET": ["user:read"],
      "PUT": ["user:update"],
      "DELETE": ["user:delete"]
    },
    "/api/v1/orders": {
      "GET": ["order:read", "order:list"],
      "POST": ["order:create"]
    },
    "/api/v1/config": {
      "GET": ["config:read"],
      "PUT": ["config:update"]
    }
  }
}
```

### 2.4 权限命名规范

采用 `资源:操作` 的命名格式：

| 权限标识 | 说明 |
|---------|------|
| `user:read` | 查看用户信息 |
| `user:list` | 列出用户列表 |
| `user:create` | 创建用户 |
| `user:update` | 更新用户信息 |
| `user:delete` | 删除用户 |
| `*` | 所有权限（超级管理员） |

### 2.5 角色继承

支持角色继承机制，子角色自动继承父角色的权限：

```json
{
  "role_hierarchy": {
    "admin": ["manager", "developer"],
    "manager": ["developer", "viewer"],
    "developer": ["viewer"],
    "viewer": []
  }
}
```

**继承规则**：
- 权限向上继承：子角色拥有父角色的所有权限
- 权限可覆盖：子角色可以定义额外的权限
- 避免循环：继承关系不能形成环路

### 2.6 RBAC实现示例

```go
type RBACAuthorizer struct {
    roleStore      RoleStore
    policyStore    PolicyStore
    cache          Cache
}

func (r *RBACAuthorizer) Authorize(ctx context.Context, req *AuthRequest) (*AuthDecision, error) {
    user := req.Subject
    
    var allPermissions []string
    for _, roleID := range user.Roles {
        role, err := r.roleStore.GetRole(ctx, roleID)
        if err != nil {
            continue
        }
        
        permissions := r.resolvePermissions(ctx, role)
        allPermissions = append(allPermissions, permissions...)
    }
    
    requiredPerm := r.getRequiredPermission(req.Resource, req.Action)
    
    if r.hasPermission(allPermissions, requiredPerm) {
        return &AuthDecision{Allowed: true, Reason: "RBAC permission granted"}, nil
    }
    
    return &AuthDecision{Allowed: false, Reason: "RBAC permission denied"}, nil
}

func (r *RBACAuthorizer) resolvePermissions(ctx context.Context, role *Role) []string {
    permissions := make([]string, 0)
    
    for _, perm := range role.Permissions {
        if perm == "*" {
            return []string{"*"}
        }
        permissions = append(permissions, perm)
    }
    
    for _, parentRoleID := range role.Inherits {
        parent, _ := r.roleStore.GetRole(ctx, parentRoleID)
        if parent != nil {
            permissions = append(permissions, r.resolvePermissions(ctx, parent)...)
        }
    }
    
    return permissions
}
```

---

## 3. ABAC 权限模型设计

### 3.1 模型概述

ABAC（Attribute-Based Access Control，基于属性的访问控制）通过主体、资源、环境等属性进行动态权限判断，提供更细粒度的访问控制。

### 3.2 核心概念

**ABAC四要素**：
- **主体**：请求发起者（用户、服务）
- **资源**：被访问的对象（API、数据、文件）
- **动作**：对资源的操作（读、写、删除）
- **环境**：上下文信息（时间、位置、设备）

### 3.3 策略模型

#### 3.3.1 策略结构

```json
{
  "policy": {
    "id": "policy_001",
    "name": "敏感数据访问策略",
    "description": "限制敏感数据的访问时间和来源",
    "effect": "allow",
    "subject": {
      "attributes": {
        "department": "研发部",
        "level": ">=3",
        "status": "active"
      }
    },
    "resource": {
      "type": "api",
      "path": "/api/v1/sensitive/*",
      "attributes": {
        "classification": "confidential"
      }
    },
    "action": ["read", "write"],
    "condition": {
      "time": {
        "range": "09:00-18:00",
        "timezone": "Asia/Shanghai"
      },
      "ip_range": "10.0.0.0/8",
      "device": {
        "type": ["corporate", "approved"],
        "security_level": ">=2"
      }
    },
    "obligations": {
      "audit_log": true,
      "data_masking": "partial"
    }
  }
}
```

#### 3.3.2 策略属性定义

**主体属性**：
```json
{
  "subject_attributes": {
    "user_id": "string",
    "username": "string",
    "email": "string",
    "department": "string",
    "level": "integer",
    "title": "string",
    "groups": ["string"],
    "created_at": "timestamp",
    "last_login": "timestamp",
    "status": "enum(active, inactive, suspended)"
  }
}
```

**资源属性**：
```json
{
  "resource_attributes": {
    "type": "enum(api, file, database, service)",
    "path": "string",
    "method": "enum(GET, POST, PUT, DELETE, PATCH)",
    "owner": "string",
    "classification": "enum(public, internal, confidential, secret)",
    "sensitivity": "integer",
    "tags": ["string"],
    "created_at": "timestamp"
  }
}
```

**环境属性**：
```json
{
  "environment_attributes": {
    "time": "timestamp",
    "timezone": "string",
    "ip_address": "string",
    "user_agent": "string",
    "device_type": "string",
    "location": {
      "country": "string",
      "city": "string",
      "latitude": "float",
      "longitude": "float"
    },
    "network": {
      "type": "enum(intranet, internet, vpn)",
      "security_level": "integer"
    }
  }
}
```

### 3.4 条件表达式

#### 3.4.1 支持的操作符

| 类别 | 操作符 | 说明 | 示例 |
|------|--------|------|------|
| 比较 | `==`, `!=` | 等于、不等于 | `department == "研发部"` |
| 比较 | `>`, `>=`, `<`, `<=` | 大小比较 | `level >= 3` |
| 逻辑 | `&&`, `\|\|`, `!` | 与、或、非 | `level >= 3 && status == "active"` |
| 集合 | `in`, `not in` | 包含、不包含 | `department in ["研发部", "产品部"]` |
| 字符串 | `startsWith`, `endsWith`, `contains` | 字符串匹配 | `username startsWith "admin"` |
| 正则 | `matches` | 正则表达式 | `email matches ".*@company\\.com"` |
| 时间 | `between` | 时间范围 | `time between "09:00" and "18:00"` |
| 网络 | `inCIDR` | IP段匹配 | `ip inCIDR "10.0.0.0/8"` |

#### 3.4.2 复杂条件示例

```json
{
  "condition": {
    "and": [
      {
        "or": [
          {"subject.department": "研发部"},
          {"subject.department": "产品部"}
        ]
      },
      {"subject.level": {">=": 3}},
      {"environment.time": {"between": ["09:00", "18:00"]}},
      {"environment.ip": {"inCIDR": "10.0.0.0/8"}}
    ]
  }
}
```

### 3.5 ABAC实现示例

```go
type ABACAuthorizer struct {
    policyStore  PolicyStore
    evaluator    ConditionEvaluator
}

func (a *ABACAuthorizer) Authorize(ctx context.Context, req *AuthRequest) (*AuthDecision, error) {
    policies, err := a.policyStore.GetApplicablePolicies(ctx, req.Resource, req.Action)
    if err != nil {
        return nil, err
    }
    
    for _, policy := range policies {
        match, err := a.evaluatePolicy(ctx, policy, req)
        if err != nil {
            continue
        }
        
        if match {
            return &AuthDecision{
                Allowed:    policy.Effect == "allow",
                Reason:     fmt.Sprintf("Matched policy: %s", policy.ID),
                Obligations: policy.Obligations,
            }, nil
        }
    }
    
    return &AuthDecision{Allowed: false, Reason: "No matching ABAC policy"}, nil
}

func (a *ABACAuthorizer) evaluatePolicy(ctx context.Context, policy *Policy, req *AuthRequest) (bool, error) {
    context := map[string]interface{}{
        "subject":     req.Subject.Attributes,
        "resource":    req.Resource.Attributes,
        "environment": req.Context,
        "action":      req.Action,
    }
    
    subjectMatch := a.matchAttributes(policy.Subject.Attributes, context["subject"])
    if !subjectMatch {
        return false, nil
    }
    
    resourceMatch := a.matchAttributes(policy.Resource.Attributes, context["resource"])
    if !resourceMatch {
        return false, nil
    }
    
    conditionMatch := a.evaluator.Evaluate(policy.Condition, context)
    
    return conditionMatch, nil
}
```

### 3.6 策略管理

#### 3.6.1 策略生命周期

```
创建 -> 测试 -> 审批 -> 发布 -> 监控 -> 更新/废弃
```

#### 3.6.2 策略版本控制

```json
{
  "policy_version": {
    "id": "policy_001",
    "version": "v2.1.0",
    "status": "active",
    "created_at": "2024-01-01T00:00:00Z",
    "updated_at": "2024-02-01T00:00:00Z",
    "created_by": "admin",
    "change_log": [
      {
        "version": "v2.1.0",
        "date": "2024-02-01T00:00:00Z",
        "changes": "增加VPN访问条件",
        "author": "admin"
      }
    ]
  }
}
```

---

## 4. 上下文透传设计

### 4.1 设计目标

- **标准化**：定义统一的HTTP Header标准
- **安全性**：防止信息篡改和伪造
- **可追溯**：支持全链路追踪
- **多租户**：支持租户隔离

### 4.2 HTTP Header标准

#### 4.2.1 标准Header定义

```http
X-User-Id: user123
X-User-Name: base64(username)
X-User-Roles: role1,role2
X-User-Permissions: perm1,perm2,perm3
X-Tenant-Id: tenant001
X-Trace-Id: abc123def456
X-Auth-Time: 1709251200000
X-Auth-Signature: hmac签名（防篡改）
```

#### 4.2.2 Header详细说明

| Header名称 | 类型 | 必填 | 说明 | 示例 |
|-----------|------|------|------|------|
| X-User-Id | string | 是 | 用户唯一标识 | `user123` |
| X-User-Name | string | 是 | 用户名（Base64编码） | `emhhbmdfc2Fu` |
| X-User-Roles | string | 是 | 角色列表（逗号分隔） | `admin,developer` |
| X-User-Permissions | string | 否 | 权限列表（逗号分隔） | `api:read,api:write` |
| X-Tenant-Id | string | 是 | 租户标识 | `tenant001` |
| X-Trace-Id | string | 是 | 追踪标识 | `abc123def456` |
| X-Auth-Time | timestamp | 是 | 认证时间戳（毫秒） | `1709251200000` |
| X-Auth-Signature | string | 是 | HMAC签名 | `sha256=...` |

#### 4.2.3 扩展Header

```http
X-User-Email: base64(email@example.com)
X-User-Department: base64(研发部)
X-User-Level: 3
X-Session-Id: session_abc123
X-Request-Source: gateway
X-Forwarded-For: 10.0.1.100, 192.168.1.1
```

### 4.3 签名机制

#### 4.3.1 签名算法

采用HMAC-SHA256算法对关键Header进行签名：

```
签名内容 = X-User-Id + X-Tenant-Id + X-Auth-Time + X-Trace-Id
签名密钥 = 共享密钥（定期轮换）
签名结果 = HMAC-SHA256(签名内容, 签名密钥)
```

#### 4.3.2 签名实现

```go
type HeaderSigner struct {
    secretKey    string
    algorithm    string
    expiryWindow time.Duration
}

func (h *HeaderSigner) Sign(headers map[string]string) string {
    signContent := fmt.Sprintf("%s%s%s%s",
        headers["X-User-Id"],
        headers["X-Tenant-Id"],
        headers["X-Auth-Time"],
        headers["X-Trace-Id"],
    )
    
    mac := hmac.New(sha256.New, []byte(h.secretKey))
    mac.Write([]byte(signContent))
    signature := mac.Sum(nil)
    
    return "sha256=" + base64.StdEncoding.EncodeToString(signature)
}

func (h *HeaderSigner) Verify(headers map[string]string, signature string) bool {
    expectedSig := h.Sign(headers)
    
    if !hmac.Equal([]byte(signature), []byte(expectedSig)) {
        return false
    }
    
    authTime, err := strconv.ParseInt(headers["X-Auth-Time"], 10, 64)
    if err != nil {
        return false
    }
    
    if time.Since(time.UnixMilli(authTime)) > h.expiryWindow {
        return false
    }
    
    return true
}
```

### 4.4 上下文构建流程

```
1. 鉴权成功
   ↓
2. 提取用户信息
   ↓
3. 构建Header Map
   ↓
4. 生成签名
   ↓
5. 注入HTTP请求
   ↓
6. 转发到后端服务
```

### 4.5 下游服务验证

下游服务需要验证Header的合法性：

```go
func ValidateAuthHeaders(r *http.Request) (*UserContext, error) {
    headers := extractHeaders(r)
    
    signature := r.Header.Get("X-Auth-Signature")
    if !signer.Verify(headers, signature) {
        return nil, errors.New("invalid signature")
    }
    
    authTime, _ := strconv.ParseInt(headers["X-Auth-Time"], 10, 64)
    if time.Since(time.UnixMilli(authTime)) > 5*time.Minute {
        return nil, errors.New("expired auth time")
    }
    
    username, _ := base64.StdEncoding.DecodeString(headers["X-User-Name"])
    
    return &UserContext{
        UserID:       headers["X-User-Id"],
        Username:     string(username),
        Roles:        strings.Split(headers["X-User-Roles"], ","),
        Permissions:  strings.Split(headers["X-User-Permissions"], ","),
        TenantID:     headers["X-Tenant-Id"],
        TraceID:      headers["X-Trace-Id"],
    }, nil
}
```

### 4.6 多租户隔离

#### 4.6.1 租户上下文

```go
type TenantContext struct {
    ID          string
    Name        string
    Plan        string
    Quotas      map[string]int64
    Features    []string
    Isolation   IsolationLevel
}

type IsolationLevel string

const (
    SharedIsolation    IsolationLevel = "shared"
    DatabaseIsolation  IsolationLevel = "database"
    SchemaIsolation    IsolationLevel = "schema"
    InstanceIsolation  IsolationLevel = "instance"
)
```

#### 4.6.2 租户隔离策略

```yaml
tenant_isolation:
  default_level: shared
  data_isolation:
    enabled: true
    method: row_level
  rate_limit:
    per_tenant: true
    default_quota: 10000
  resource_quota:
    cpu: 1000m
    memory: 1Gi
    storage: 10Gi
```

---

## 5. JWT/Session 会话管理方案

### 5.1 JWT无状态方案

#### 5.1.1 JWT结构

```
Header.Payload.Signature
```

**Header**：
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key_001"
}
```

**Payload**：
```json
{
  "iss": "https://gateway.example.com",
  "sub": "user123",
  "aud": "api",
  "exp": 1709254800,
  "iat": 1709251200,
  "nbf": 1709251200,
  "jti": "token_abc123",
  "user": {
    "id": "user123",
    "username": "zhang_san",
    "email": "zhangsan@example.com",
    "roles": ["admin", "developer"],
    "tenant_id": "tenant001"
  },
  "permissions": ["api:read", "api:write"]
}
```

#### 5.1.2 JWT配置

```yaml
jwt:
  algorithm: RS256
  private_key: "${JWT_PRIVATE_KEY}"
  public_key: "${JWT_PUBLIC_KEY}"
  issuer: "https://gateway.example.com"
  audience: "api"
  expiry: 3600
  refresh_expiry: 86400
  not_before: 0
  claims:
    - user_id
    - username
    - email
    - roles
    - permissions
    - tenant_id
```

#### 5.1.3 JWT生成

```go
type JWTManager struct {
    privateKey    *rsa.PrivateKey
    publicKey     *rsa.PublicKey
    issuer        string
    audience      string
    expiry        time.Duration
    refreshExpiry time.Duration
}

func (j *JWTManager) GenerateToken(user *UserIdentity) (string, error) {
    now := time.Now()
    
    claims := jwt.MapClaims{
        "iss": j.issuer,
        "sub": user.ID,
        "aud": j.audience,
        "exp": now.Add(j.expiry).Unix(),
        "iat": now.Unix(),
        "nbf": now.Unix(),
        "jti": uuid.New().String(),
        "user": map[string]interface{}{
            "id":        user.ID,
            "username":  user.Username,
            "email":     user.Email,
            "roles":     user.Roles,
            "tenant_id": user.TenantID,
        },
        "permissions": user.Permissions,
    }
    
    token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
    token.Header["kid"] = "key_001"
    
    return token.SignedString(j.privateKey)
}

func (j *JWTManager) ValidateToken(tokenString string) (*jwt.Token, error) {
    token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
        if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
            return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
        }
        return j.publicKey, nil
    })
    
    if err != nil {
        return nil, err
    }
    
    if !token.Valid {
        return nil, errors.New("invalid token")
    }
    
    return token, nil
}
```

### 5.2 Redis有状态方案

#### 5.2.1 Session结构

```json
{
  "session_id": "sess_abc123def456",
  "user_id": "user123",
  "username": "zhang_san",
  "roles": ["admin", "developer"],
  "permissions": ["api:read", "api:write"],
  "tenant_id": "tenant001",
  "created_at": 1709251200,
  "updated_at": 1709254800,
  "expires_at": 1709337600,
  "ip_address": "10.0.1.100",
  "user_agent": "Mozilla/5.0...",
  "metadata": {
    "device_id": "device_001",
    "login_method": "password"
  }
}
```

#### 5.2.2 Redis存储设计

```
Key格式: session:{session_id}
Value: JSON序列化的Session对象
TTL: 会话过期时间

索引:
  user_sessions:{user_id} -> Set<session_id>
  tenant_sessions:{tenant_id} -> Set<session_id>
```

#### 5.2.3 Session管理

```go
type RedisSessionManager struct {
    client     *redis.Client
    prefix     string
    defaultTTL time.Duration
}

func (r *RedisSessionManager) CreateSession(ctx context.Context, user *UserIdentity) (*Session, error) {
    sessionID := "sess_" + uuid.New().String()
    now := time.Now()
    
    session := &Session{
        ID:          sessionID,
        UserID:      user.ID,
        Username:    user.Username,
        Roles:       user.Roles,
        Permissions: user.Permissions,
        TenantID:    user.TenantID,
        CreatedAt:   now,
        UpdatedAt:   now,
        ExpiresAt:   now.Add(r.defaultTTL),
    }
    
    data, err := json.Marshal(session)
    if err != nil {
        return nil, err
    }
    
    key := r.prefix + sessionID
    if err := r.client.Set(ctx, key, data, r.defaultTTL).Err(); err != nil {
        return nil, err
    }
    
    userSessionsKey := fmt.Sprintf("user_sessions:%s", user.ID)
    r.client.SAdd(ctx, userSessionsKey, sessionID)
    
    return session, nil
}

func (r *RedisSessionManager) GetSession(ctx context.Context, sessionID string) (*Session, error) {
    key := r.prefix + sessionID
    data, err := r.client.Get(ctx, key).Bytes()
    if err != nil {
        return nil, err
    }
    
    var session Session
    if err := json.Unmarshal(data, &session); err != nil {
        return nil, err
    }
    
    return &session, nil
}

func (r *RedisSessionManager) DeleteSession(ctx context.Context, sessionID string) error {
    session, err := r.GetSession(ctx, sessionID)
    if err != nil {
        return err
    }
    
    key := r.prefix + sessionID
    if err := r.client.Del(ctx, key).Err(); err != nil {
        return err
    }
    
    userSessionsKey := fmt.Sprintf("user_sessions:%s", session.UserID)
    r.client.SRem(ctx, userSessionsKey, sessionID)
    
    return nil
}

func (r *RedisSessionManager) DeleteUserSessions(ctx context.Context, userID string) error {
    userSessionsKey := fmt.Sprintf("user_sessions:%s", userID)
    sessionIDs, err := r.client.SMembers(ctx, userSessionsKey).Result()
    if err != nil {
        return err
    }
    
    for _, sessionID := range sessionIDs {
        r.DeleteSession(ctx, sessionID)
    }
    
    return nil
}
```

### 5.3 混合模式方案

#### 5.3.1 混合模式架构

```
JWT短期Token + Redis黑名单 + Redis Refresh Token
```

**工作流程**：
1. 用户登录：生成JWT（短期，如5分钟）和Refresh Token（长期，如24小时）
2. 请求验证：验证JWT签名 + 检查Redis黑名单
3. Token刷新：使用Refresh Token获取新的JWT
4. 主动失效：将JWT加入Redis黑名单

#### 5.3.2 混合模式实现

```go
type HybridSessionManager struct {
    jwtManager    *JWTManager
    redisManager  *RedisSessionManager
    blacklist     *TokenBlacklist
    jwtExpiry     time.Duration
    refreshExpiry time.Duration
}

func (h *HybridSessionManager) Login(ctx context.Context, user *UserIdentity) (*TokenPair, error) {
    jwtToken, err := h.jwtManager.GenerateToken(user)
    if err != nil {
        return nil, err
    }
    
    refreshToken := "refresh_" + uuid.New().String()
    session := &Session{
        ID:          refreshToken,
        UserID:      user.ID,
        Username:    user.Username,
        Roles:       user.Roles,
        Permissions: user.Permissions,
        TenantID:    user.TenantID,
        CreatedAt:   time.Now(),
        ExpiresAt:   time.Now().Add(h.refreshExpiry),
    }
    
    if err := h.redisManager.CreateSession(ctx, session); err != nil {
        return nil, err
    }
    
    return &TokenPair{
        AccessToken:  jwtToken,
        RefreshToken: refreshToken,
        ExpiresIn:    int(h.jwtExpiry.Seconds()),
        TokenType:    "Bearer",
    }, nil
}

func (h *HybridSessionManager) ValidateToken(ctx context.Context, tokenString string) (*UserIdentity, error) {
    token, err := h.jwtManager.ValidateToken(tokenString)
    if err != nil {
        return nil, err
    }
    
    claims := token.Claims.(jwt.MapClaims)
    jti := claims["jti"].(string)
    
    if h.blacklist.IsBlacklisted(ctx, jti) {
        return nil, errors.New("token is blacklisted")
    }
    
    user := claims["user"].(map[string]interface{})
    
    return &UserIdentity{
        ID:          user["id"].(string),
        Username:    user["username"].(string),
        Email:       user["email"].(string),
        Roles:       toStringSlice(user["roles"]),
        Permissions: toStringSlice(claims["permissions"]),
        TenantID:    user["tenant_id"].(string),
    }, nil
}

func (h *HybridSessionManager) RefreshToken(ctx context.Context, refreshToken string) (*TokenPair, error) {
    session, err := h.redisManager.GetSession(ctx, refreshToken)
    if err != nil {
        return nil, errors.New("invalid refresh token")
    }
    
    if time.Now().After(session.ExpiresAt) {
        h.redisManager.DeleteSession(ctx, refreshToken)
        return nil, errors.New("refresh token expired")
    }
    
    user := &UserIdentity{
        ID:          session.UserID,
        Username:    session.Username,
        Roles:       session.Roles,
        Permissions: session.Permissions,
        TenantID:    session.TenantID,
    }
    
    jwtToken, err := h.jwtManager.GenerateToken(user)
    if err != nil {
        return nil, err
    }
    
    return &TokenPair{
        AccessToken:  jwtToken,
        RefreshToken: refreshToken,
        ExpiresIn:    int(h.jwtExpiry.Seconds()),
        TokenType:    "Bearer",
    }, nil
}

func (h *HybridSessionManager) Logout(ctx context.Context, tokenString string) error {
    token, err := h.jwtManager.ValidateToken(tokenString)
    if err != nil {
        return err
    }
    
    claims := token.Claims.(jwt.MapClaims)
    jti := claims["jti"].(string)
    exp := time.Unix(int64(claims["exp"].(float64)), 0)
    
    return h.blacklist.Add(ctx, jti, exp)
}
```

#### 5.3.3 Token黑名单

```go
type TokenBlacklist struct {
    client *redis.Client
    prefix string
}

func (b *TokenBlacklist) Add(ctx context.Context, jti string, expiry time.Time) error {
    key := b.prefix + jti
    ttl := time.Until(expiry)
    
    if ttl <= 0 {
        return nil
    }
    
    return b.client.Set(ctx, key, "1", ttl).Err()
}

func (b *TokenBlacklist) IsBlacklisted(ctx context.Context, jti string) bool {
    key := b.prefix + jti
    exists, _ := b.client.Exists(ctx, key).Result()
    return exists > 0
}
```

### 5.4 会话安全策略

#### 5.4.1 安全配置

```yaml
session_security:
  concurrent_sessions:
    max_per_user: 5
    strategy: "oldest_first"
  
  session_timeout:
    absolute: 86400
    idle: 1800
    sliding: true
  
  token_security:
    secure_cookie: true
    http_only: true
    same_site: "strict"
  
  brute_force_protection:
    enabled: true
    max_attempts: 5
    lockout_duration: 900
    tracking_window: 3600
```

#### 5.4.2 会话事件审计

```go
type SessionEvent struct {
    EventType   string    `json:"event_type"`
    SessionID   string    `json:"session_id"`
    UserID      string    `json:"user_id"`
    TenantID    string    `json:"tenant_id"`
    IPAddress   string    `json:"ip_address"`
    UserAgent   string    `json:"user_agent"`
    Timestamp   time.Time `json:"timestamp"`
    Reason      string    `json:"reason"`
}

const (
    EventLogin         = "login"
    EventLogout        = "logout"
    EventRefresh       = "refresh"
    EventExpire        = "expire"
    EventKickout       = "kickout"
    EventForceLogout   = "force_logout"
    EventInvalidAccess = "invalid_access"
)
```

### 5.5 会话监控

#### 5.5.1 监控指标

```yaml
session_metrics:
  - active_sessions_total
  - sessions_created_total
  - sessions_destroyed_total
  - session_duration_seconds
  - concurrent_sessions_per_user
  - token_validation_errors_total
  - refresh_token_usage_total
  - blacklist_size
```

#### 5.5.2 告警规则

```yaml
alerts:
  - name: HighSessionCount
    expr: active_sessions_total > 10000
    severity: warning
    
  - name: SessionValidationErrors
    expr: rate(token_validation_errors_total[5m]) > 100
    severity: critical
    
  - name: BlacklistSizeGrowing
    expr: rate(blacklist_size[1h]) > 1000
    severity: warning
```

---

## 6. 最佳实践建议

### 6.1 鉴权策略选择

| 场景 | 推荐方案 | 理由 |
|------|---------|------|
| 微服务API | JWT + RBAC | 无状态、易扩展、性能好 |
| 企业内部系统 | Session + RBAC | 可控性强、支持踢出 |
| 多租户SaaS | JWT + ABAC | 细粒度控制、灵活策略 |
| 金融系统 | 混合模式 + ABAC | 安全性高、可审计 |

### 6.2 性能优化建议

1. **缓存策略**：缓存用户权限信息，减少数据库查询
2. **批量验证**：支持批量Token验证
3. **异步审计**：会话事件异步记录
4. **连接池**：Redis连接池优化

### 6.3 安全加固建议

1. **密钥轮换**：定期轮换JWT签名密钥
2. **Token绑定**：Token与IP、设备绑定
3. **异常检测**：检测异常登录行为
4. **最小权限**：遵循最小权限原则

---

## 7. 参考资料

- [RFC 7519 - JSON Web Token (JWT)](https://tools.ietf.org/html/rfc7519)
- [NIST ABAC Guide](https://csrc.nist.gov/projects/attribute-based-access-control)
- [OAuth 2.0 Specification](https://oauth.net/2/)
- [OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html)
