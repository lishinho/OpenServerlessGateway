# OpenServerlessGateway 快速开始

本文档提供 OpenServerlessGateway (OSG) 的标准化接入 SOP，帮助您快速完成业务模块的网关接入。

---

## 一、接入概述

### 1.1 接入优势

| 优势 | 说明 |
|------|------|
| 零代码修改 | 业务模块无需修改任何代码 |
| 分钟级接入 | 只需完成服务注册和配置 |
| 低代码配置 | 通过配置中心/控制台完成配置 |
| 自动化文档 | 接入后自动生成接口文档 |

### 1.2 接入流程总览

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   阶段1      │    │   阶段2      │    │   阶段3      │    │   阶段4      │
│   接入准备   │ ──►│   网关配置   │ ──►│   联调测试   │ ──►│   发布上线   │
└──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
```

---

## 二、阶段1：接入准备

### 2.1 环境检查清单

在开始接入前，请确认以下环境已准备就绪：

| 检查项 | 要求 | 检查命令 |
|--------|------|----------|
| 注册中心 | 已部署 Nacos/Consul/Eureka | 访问控制台确认 |
| 配置中心 | 已部署 Nacos/Apollo | 访问控制台确认 |
| Redis | 已部署并可访问 | `redis-cli ping` |
| 数据库 | 已创建 osg 数据库 | 连接测试 |

### 2.2 业务模块准备

#### 2.2.1 服务注册

**微服务模块（Spring Cloud）**

```yaml
# application.yml
spring:
  application:
    name: your-service-name
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
```

**微服务模块（Dubbo）**

```yaml
# application.yml
dubbo:
  application:
    name: your-dubbo-service
  registry:
    address: nacos://nacos:8848
```

**Serverless 函数（Knative）**

```yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: your-function
spec:
  template:
    spec:
      containers:
      - image: your-function-image
```

#### 2.2.2 确认协议类型

| 协议类型 | 说明 | 配置方式 |
|----------|------|----------|
| HTTP/HTTPS | RESTful API | 直接映射 |
| Dubbo | RPC 协议 | 协议转换 |
| gRPC | 高性能 RPC | 协议转换 |
| SOFA | 蚂蚁金服 RPC | 协议转换 |

#### 2.2.3 准备接口元数据

收集以下接口信息：

| 元数据项 | 说明 | 示例 |
|----------|------|------|
| 服务名称 | 后端服务标识 | user-service |
| 接口名称 | 接口全限定名 | com.example.UserService |
| 方法名称 | 方法名 | getUserById |
| 参数类型 | 参数类型列表 | Long, String |
| 返回类型 | 返回值类型 | UserDTO |
| 接口路径 | HTTP 映射路径 | /api/user/{id} |

---

## 三、阶段2：网关配置

### 3.1 API 元数据配置

#### 3.1.1 通过配置中心配置

```yaml
# Nacos 配置：osg-api-config.yaml
osg:
  api:
    routes:
      - id: user-service-route
        uri: dubbo://user-service
        predicates:
          - Path=/api/user/**
        filters:
          - name: RateLimit
            args:
              limit: 100
              period: 60
        metadata:
          service: user-service
          interface: com.example.UserService
          method: getUserById
          params:
            - name: id
              type: Long
              source: path
```

#### 3.1.2 通过控制台配置

1. 登录 OSG 控制台
2. 进入「API 管理」→「新建 API」
3. 填写 API 基本信息：
   - API 名称：获取用户信息
   - API 路径：`/api/user/{id}`
   - 请求方式：GET
4. 配置后端服务：
   - 协议类型：Dubbo
   - 服务名称：user-service
   - 接口名称：com.example.UserService
   - 方法名称：getUserById
5. 配置参数映射：
   - 参数名：id
   - 参数类型：Long
   - 来源：path
6. 保存并发布

### 3.2 基础能力配置

#### 3.2.1 限流配置

```yaml
osg:
  ratelimit:
    rules:
      - id: user-api-limit
        path: /api/user/**
        limit: 100        # 限制次数
        period: 60        # 时间窗口（秒）
        unit: ip          # 限流维度：ip/user/interface
```

#### 3.2.2 降级配置

```yaml
osg:
  degrade:
    rules:
      - id: user-api-degrade
        path: /api/user/**
        threshold: 0.5    # 错误率阈值
        timeWindow: 30    # 时间窗口（秒）
        fallback:
          type: static    # static/redirect
          data: |
            {"code": 503, "message": "服务暂时不可用"}
```

#### 3.2.3 鉴权配置

```yaml
osg:
  auth:
    enabled: true
    type: rbac           # rbac/abac
    exclude-paths:
      - /api/public/**
      - /actuator/**
    token:
      type: jwt
      secret: your-secret-key
      expiration: 3600
```

#### 3.2.4 Mock 配置

```yaml
osg:
  mock:
    enabled: true
    rules:
      - path: /api/user/**
        enabled: true
        data: |
          {
            "code": 200,
            "data": {
              "id": 1,
              "name": "Mock User"
            }
          }
```

### 3.3 协议转换配置

#### 3.3.1 HTTP → Dubbo 转换

```yaml
osg:
  protocol:
    converter:
      - type: dubbo
        config:
          application: osg-gateway
          registry: nacos://nacos:8848
          timeout: 3000
          retries: 2
```

#### 3.3.2 HTTP → gRPC 转换

```yaml
osg:
  protocol:
    converter:
      - type: grpc
        config:
          address: grpc-service:9090
          useTLS: false
          timeout: 5000
```

---

## 四、阶段3：联调测试

### 4.1 自动化接口文档

#### 4.1.1 访问接口文档

配置完成后，OSG 自动生成 Swagger/OpenAPI 3.0 接口文档：

```
http://osg-gateway:8080/swagger-ui.html
```

或 OpenAPI 3.0 格式：

```
http://osg-gateway:8080/v3/api-docs
```

#### 4.1.2 在线调试

1. 打开 Swagger UI 页面
2. 选择要测试的 API
3. 点击「Try it out」
4. 填写参数
5. 点击「Execute」执行请求
6. 查看响应结果

### 4.2 Mock 调试

#### 4.2.1 开启 Mock

```yaml
# 配置中心开启 Mock
osg:
  mock:
    enabled: true
    rules:
      - path: /api/user/**
        enabled: true
```

#### 4.2.2 Mock 调试流程

```
前端开发                    后端开发
    │                          │
    ▼                          ▼
┌─────────┐              ┌─────────┐
│ 调用Mock │              │ 开发接口 │
│ 接口     │              │         │
└─────────┘              └─────────┘
    │                          │
    │      并行开发            │
    │◄────────────────────────►│
    │                          │
    ▼                          ▼
┌─────────────────────────────────┐
│         联调测试                 │
└─────────────────────────────────┘
```

### 4.3 灰度引流测试

#### 4.3.1 配置灰度规则

```yaml
osg:
  canary:
    enabled: true
    rules:
      - id: user-service-canary
        path: /api/user/**
        weight: 10        # 10% 流量到新版本
        headers:
          - name: X-Canary
            value: true
        params:
          - name: userId
            pattern: "^1[0-9]{9}$"
```

#### 4.3.2 验证灰度效果

```bash
# 发送带灰度标记的请求
curl -H "X-Canary: true" http://osg-gateway:8080/api/user/1

# 观察监控指标
# 访问 Grafana 查看流量分布
```

---

## 五、阶段4：发布上线

### 5.1 灰度发布流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        灰度发布流程                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1% ──► 5% ──► 10% ──► 25% ──► 50% ──► 100%                    │
│                                                                 │
│  每个阶段：                                                      │
│  ├── 监控错误率和响应时间                                        │
│  ├── 检查业务指标是否正常                                        │
│  └── 确认无问题后进入下一阶段                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 监控指标

#### 5.2.1 关键监控指标

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| QPS | 每秒请求数 | 根据容量规划 |
| 响应时间 | P99 延迟 | > 500ms |
| 错误率 | 请求失败比例 | > 1% |
| 网关 CPU | CPU 使用率 | > 80% |
| 网关内存 | 内存使用率 | > 85% |

#### 5.2.2 监控面板访问

```
# Prometheus
http://prometheus:9090

# Grafana
http://grafana:3000
```

### 5.3 全量切流

#### 5.3.1 切流前检查

- [ ] 灰度期间无严重错误
- [ ] 响应时间符合预期
- [ ] 业务指标正常
- [ ] 日志无异常告警

#### 5.3.2 执行全量切流

```yaml
# 配置中心修改灰度权重
osg:
  canary:
    rules:
      - id: user-service-canary
        path: /api/user/**
        weight: 100       # 全量切流
```

### 5.4 接入完成确认

接入完成后，请确认以下事项：

| 确认项 | 状态 |
|--------|------|
| API 文档已生成 | ☐ |
| 限流规则已生效 | ☐ |
| 鉴权规则已生效 | ☐ |
| 监控指标正常 | ☐ |
| 日志采集正常 | ☐ |
| 告警规则已配置 | ☐ |

---

## 六、常见问题

### 6.1 服务发现失败

**问题**：网关无法发现后端服务

**排查步骤**：
1. 检查注册中心是否正常运行
2. 确认服务已正确注册
3. 检查网络连通性
4. 查看网关日志

```bash
# 检查服务注册状态（Nacos）
curl http://nacos:8848/nacos/v1/ns/instance/list?serviceName=user-service
```

### 6.2 协议转换失败

**问题**：HTTP 请求无法正确转换为 RPC 调用

**排查步骤**：
1. 检查协议转换配置
2. 确认接口元数据正确
3. 检查参数类型匹配
4. 查看转换日志

### 6.3 限流不生效

**问题**：配置了限流规则但未生效

**排查步骤**：
1. 检查 Redis 连接状态
2. 确认限流配置已发布
3. 检查限流维度配置
4. 查看限流日志

```bash
# 检查 Redis 连接
redis-cli -h redis-host -p 6379 ping
```

### 6.4 鉴权失败

**问题**：请求返回 401 未授权

**排查步骤**：
1. 检查 Token 是否有效
2. 确认鉴权配置正确
3. 检查权限点配置
4. 查看鉴权日志

---

## 七、最佳实践

### 7.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 服务名称 | 小写 + 连字符 | user-service |
| API 路径 | 小写 + 连字符 | /api/user-info |
| 接口名称 | 驼峰命名 | UserService |
| 方法名称 | 驼峰命名 | getUserById |

### 7.2 版本管理

```yaml
# API 版本控制
osg:
  api:
    routes:
      - id: user-service-v1
        path: /api/v1/user/**
        uri: dubbo://user-service-v1
        
      - id: user-service-v2
        path: /api/v2/user/**
        uri: dubbo://user-service-v2
```

### 7.3 安全建议

- 所有生产环境 API 启用鉴权
- 敏感接口配置 IP 白名单
- 定期轮换 Token 密钥
- 开启访问日志审计

---

## 八、下一步

接入完成后，您可以：

1. 📖 阅读 [核心特性文档](./features.md) 了解更多功能
2. 🚀 阅读 [部署指南](./deployment.md) 了解生产部署
3. 🔧 阅读 [路线图](./roadmap.md) 了解未来规划
4. 🤝 加入 [社区](./community.md) 参与讨论
