# 灰度发布方案设计

## 概述

本文档详细描述了 OpenServerlessGateway 的灰度发布方案设计，包括灰度决策引擎架构、规则配置规范、网关层灰度方案、业务服务层灰度方案以及完整的灰度流程设计。

## 设计原则

1. **网关层灰度**：网关自身支持灰度发布，新版本网关可逐步承接流量
2. **服务层灰度**：支持对后端服务的灰度引流，实现服务版本平滑升级
3. **多维规则**：支持按用户、IP、Header、权重等多维度灰度规则
4. **实时生效**：灰度规则动态配置，实时生效，无需重启

---

## 1. 灰度决策引擎架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                       灰度决策引擎                           │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    规则匹配器                         │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │   │
│  │  │用户规则 │ │IP规则   │ │Header   │ │权重规则 │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    流量分发器                         │   │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐       │   │
│  │  │版本路由表 │  │ 实例选择器 │  │ 状态监控  │       │   │
│  │  └───────────┘  └───────────┘  └───────────┘       │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    服务注册中心                       │   │
│  │  ┌───────────────────────────────────────────────┐ │   │
│  │  │ Service V1 (stable)  <- 90% 流量               │ │   │
│  │  │ Service V2 (gray)    <- 10% 流量               │ │   │
│  │  └───────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 核心组件说明

#### 1.2.1 规则匹配器

规则匹配器负责解析和匹配灰度规则，支持多种匹配策略：

| 组件 | 功能 | 实现方式 |
|------|------|----------|
| 用户规则 | 根据用户ID进行灰度匹配 | 支持白名单、黑名单、正则匹配 |
| IP规则 | 根据请求来源IP进行灰度匹配 | 支持IP列表、CIDR网段匹配 |
| Header规则 | 根据HTTP Header进行灰度匹配 | 支持精确匹配、正则匹配 |
| 权重规则 | 根据配置的权重比例进行流量分配 | 基于一致性哈希或随机算法 |

#### 1.2.2 流量分发器

流量分发器负责将匹配到的请求路由到目标服务版本：

| 组件 | 功能 | 说明 |
|------|------|------|
| 版本路由表 | 维护服务版本与实例的映射关系 | 实时更新，支持动态配置 |
| 实例选择器 | 从目标版本实例列表中选择具体实例 | 支持多种负载均衡策略 |
| 状态监控 | 监控灰度实例的健康状态 | 异常时自动触发回滚 |

#### 1.2.3 服务注册中心

服务注册中心管理服务实例的注册与发现：

- 服务启动时注册到注册中心，携带版本标签（如 `version=v1` 或 `version=v2`）
- 网关订阅服务变更事件，实时更新路由表
- 支持多种注册中心：Nacos、Consul、Eureka、Zookeeper 等

### 1.3 决策流程

```
请求到达
    │
    ▼
┌─────────────────┐
│  提取灰度标识    │  ← 从Header/Cookie/Token中提取
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  加载灰度规则    │  ← 从配置中心获取最新规则
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  规则优先级排序  │  ← 按priority字段降序排列
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  逐条匹配规则    │  ← 匹配成功则返回目标版本
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  选择目标实例    │  ← 根据版本标签筛选实例
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  转发请求        │  ← 调用目标服务实例
└─────────────────┘
```

---

## 2. 灰度规则配置规范

### 2.1 规则配置结构

```yaml
gray_rules:
  - rule_id: user-gray-001
    enabled: true
    description: "内部用户灰度规则"
    match_conditions:
      - type: user_id
        operator: in
        values: ["user001", "user002", "user003"]
      - type: header
        key: X-Gray-Version
        operator: eq
        value: "v2"
    target_version: v2
    priority: 100
    
  - rule_id: weight-gray-001
    enabled: true
    description: "权重灰度规则"
    match_conditions:
      - type: weight
        v1_percentage: 90
        v2_percentage: 10
    target_version: auto
    priority: 1
```

### 2.2 规则字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| rule_id | string | 是 | 规则唯一标识 |
| enabled | boolean | 是 | 是否启用该规则 |
| description | string | 否 | 规则描述信息 |
| match_conditions | array | 是 | 匹配条件列表，多个条件为AND关系 |
| target_version | string | 是 | 目标服务版本，`auto`表示由权重决定 |
| priority | integer | 是 | 优先级，数值越大优先级越高 |

### 2.3 匹配条件类型

#### 2.3.1 用户ID匹配

```yaml
- type: user_id
  operator: in | not_in | regex
  values: ["user001", "user002"]
```

| 操作符 | 说明 | 示例 |
|--------|------|------|
| in | 用户ID在列表中 | `values: ["user001", "user002"]` |
| not_in | 用户ID不在列表中 | `values: ["user001", "user002"]` |
| regex | 用户ID匹配正则表达式 | `pattern: "^admin_.*"` |

#### 2.3.2 IP匹配

```yaml
- type: ip
  operator: in | not_in | cidr
  values: ["192.168.1.1", "10.0.0.1"]
  cidr: "192.168.0.0/16"
```

| 操作符 | 说明 | 示例 |
|--------|------|------|
| in | IP在列表中 | `values: ["192.168.1.1"]` |
| not_in | IP不在列表中 | `values: ["192.168.1.1"]` |
| cidr | IP在指定网段内 | `cidr: "192.168.0.0/16"` |

#### 2.3.3 Header匹配

```yaml
- type: header
  key: X-Gray-Version
  operator: eq | neq | regex | exists
  value: "v2"
```

| 操作符 | 说明 | 示例 |
|--------|------|------|
| eq | 精确等于 | `value: "v2"` |
| neq | 不等于 | `value: "v2"` |
| regex | 正则匹配 | `pattern: "^v2.*"` |
| exists | Header存在 | 无需value字段 |

#### 2.3.4 权重匹配

```yaml
- type: weight
  v1_percentage: 90
  v2_percentage: 10
```

权重规则根据配置的比例随机分配流量到不同版本。

#### 2.3.5 业务属性匹配

```yaml
- type: business
  attribute: tenant_id
  operator: in
  values: ["tenant001", "tenant002"]
```

支持根据业务属性（如租户ID、地区、渠道等）进行灰度匹配。

### 2.4 规则优先级与冲突处理

1. **优先级规则**：按 `priority` 字段降序排列，优先匹配高优先级规则
2. **首次匹配**：匹配到第一条规则后即返回结果，不再继续匹配
3. **默认规则**：建议配置一条低优先级的默认规则，作为兜底策略

```yaml
gray_rules:
  - rule_id: default-rule
    enabled: true
    description: "默认规则-路由到稳定版本"
    match_conditions:
      - type: weight
        v1_percentage: 100
        v2_percentage: 0
    target_version: v1
    priority: 0
```

---

## 3. 网关层灰度方案

### 3.1 架构设计

```
                    ┌─────────────────┐
                    │   负载均衡器     │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
       ┌──────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐
       │ Gateway V1  │ │ Gateway V1 │ │ Gateway V2 │
       │ (stable)    │ │ (stable)   │ │ (gray)     │
       │   90%       │ │   90%      │ │   10%      │
       └─────────────┘ └────────────┘ └────────────┘
```

### 3.2 实现方式

网关层灰度通过 K8s Service 或负载均衡器配置权重实现：

#### 3.2.1 Kubernetes Service 方式

```yaml
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
spec:
  selector:
    app: gateway
  ports:
    - port: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service-v1
spec:
  selector:
    app: gateway
    version: v1
  ports:
    - port: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service-v2
spec:
  selector:
    app: gateway
    version: v2
  ports:
    - port: 8080
```

#### 3.2.2 Ingress 流量分割

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: gateway-ingress
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"
spec:
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: gateway-service-v2
                port:
                  number: 8080
```

### 3.3 网关版本标识

网关实例启动时通过环境变量或配置文件标识版本：

```yaml
gateway:
  version: v2
  labels:
    version: v2
    environment: gray
```

---

## 4. 业务服务层灰度方案

### 4.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                       Gateway Cluster                        │
└───────────────────────────┬─────────────────────────────────┘
                            │
                    ┌───────▼───────┐
                    │  灰度决策引擎  │
                    └───────┬───────┘
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
  ┌──────▼──────┐    ┌──────▼──────┐    ┌──────▼──────┐
  │ User Service│    │ User Service│    │ User Service│
  │   V1        │    │   V1        │    │   V2        │
  │ (stable)    │    │ (stable)    │    │ (gray)      │
  │ metadata:   │    │ metadata:   │    │ metadata:   │
  │ version=v1  │    │ version=v1  │    │ version=v2  │
  └─────────────┘    └─────────────┘    └─────────────┘
```

### 4.2 服务注册与版本标签

服务注册时携带版本标签，网关根据灰度规则路由到对应版本：

#### 4.2.1 Spring Cloud 注册示例

```yaml
spring:
  application:
    name: user-service
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        metadata:
          version: v2
          env: gray
```

#### 4.2.2 Dubbo 注册示例

```yaml
dubbo:
  application:
    name: user-service
  registry:
    address: nacos://nacos:8848
  provider:
    parameters:
      version: v2
      env: gray
```

### 4.3 路由策略实现

#### 4.3.1 基于元数据的路由

网关从注册中心获取服务实例列表，根据元数据中的 `version` 字段进行筛选：

```java
public class GrayRouteLocator {
    
    public List<ServiceInstance> selectInstances(String serviceId, String targetVersion) {
        List<ServiceInstance> allInstances = discoveryClient.getInstances(serviceId);
        
        return allInstances.stream()
            .filter(instance -> {
                String version = instance.getMetadata().get("version");
                return targetVersion.equals(version);
            })
            .collect(Collectors.toList());
    }
}
```

#### 4.3.2 负载均衡策略

```java
public class GrayLoadBalancer implements ReactorServiceInstanceLoadBalancer {
    
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        String targetVersion = grayEngine.decideVersion(request);
        List<ServiceInstance> instances = selectInstances(serviceId, targetVersion);
        ServiceInstance instance = loadBalancer.choose(instances);
        return Mono.just(new DefaultResponse(instance));
    }
}
```

---

## 5. 灰度流程设计

### 5.1 完整流程概览

```
┌─────────────────────────────────────────────────────────────────┐
│                        灰度发布完整流程                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐  │
│  │服务注册   │ -> │规则配置   │ -> │请求处理   │ -> │监控回滚   │  │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 服务注册阶段

```
服务启动 -> 注册到注册中心 -> 携带版本标签(version=v1/v2)
```

#### 详细步骤：

1. **服务启动**：新版本服务部署启动
2. **版本标识**：通过配置或环境变量设置版本标签
3. **服务注册**：向注册中心注册服务实例，携带元数据
4. **健康检查**：注册中心进行健康检查，确认服务可用
5. **通知网关**：网关订阅服务变更，更新本地路由表

```yaml
服务注册数据结构:
{
  "serviceId": "user-service",
  "instanceId": "192.168.1.100:8080",
  "host": "192.168.1.100",
  "port": 8080,
  "metadata": {
    "version": "v2",
    "env": "gray",
    "weight": "100"
  },
  "status": "UP"
}
```

### 5.3 规则配置阶段

```
配置灰度规则 -> 推送到配置中心 -> 网关实时监听更新
```

#### 详细步骤：

1. **规则编辑**：通过管理后台或配置文件编辑灰度规则
2. **规则校验**：校验规则语法和逻辑正确性
3. **推送到配置中心**：将规则推送到 Nacos/Apollo 等配置中心
4. **网关监听**：网关订阅配置变更事件
5. **实时更新**：网关接收到变更后，实时更新内存中的规则缓存

```java
@Configuration
@RefreshScope
public class GrayRuleConfig {
    
    @NacosValue(value = "${gray.rules}", autoRefreshed = true)
    private String grayRules;
    
    @Bean
    public GrayRuleManager grayRuleManager() {
        return new GrayRuleManager(grayRules);
    }
}
```

### 5.4 请求处理阶段

```
请求到达 -> 提取灰度标识 -> 匹配灰度规则 -> 选择目标版本 -> 转发请求
```

#### 详细步骤：

1. **请求到达**：请求到达网关
2. **提取灰度标识**：
   - 从 HTTP Header 提取：`X-User-Id`、`X-Gray-Version` 等
   - 从 Cookie 提取
   - 从 JWT Token 提取用户信息
   - 获取请求来源 IP

3. **匹配灰度规则**：
   - 按优先级排序规则列表
   - 逐条匹配规则条件
   - 返回匹配到的目标版本

4. **选择目标实例**：
   - 根据版本标签筛选服务实例
   - 应用负载均衡策略选择具体实例

5. **转发请求**：
   - 将请求转发到目标实例
   - 记录灰度日志和指标

```java
public class GrayFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        GrayContext context = extractGrayContext(request);
        String targetVersion = grayEngine.match(context);
        
        exchange.getAttributes().put(GRAY_VERSION_KEY, targetVersion);
        
        return chain.filter(exchange);
    }
    
    private GrayContext extractGrayContext(ServerHttpRequest request) {
        return GrayContext.builder()
            .userId(request.getHeaders().getFirst("X-User-Id"))
            .clientIp(request.getRemoteAddress().getAddress().getHostAddress())
            .headers(request.getHeaders().toSingleValueMap())
            .build();
    }
}
```

### 5.5 监控回滚阶段

```
监控灰度服务指标 -> 发现异常 -> 自动/手动回滚流量
```

#### 5.5.1 监控指标

| 指标类型 | 指标名称 | 说明 |
|----------|----------|------|
| 可用性 | 错误率 | 灰度版本请求错误率 |
| 可用性 | 可用性百分比 | 服务可用性 SLA |
| 性能 | 响应时间 P99 | 99% 请求的响应时间 |
| 性能 | 响应时间 P95 | 95% 请求的响应时间 |
| 流量 | QPS | 每秒请求数 |
| 业务 | 业务成功率 | 业务层面成功率 |

#### 5.5.2 自动回滚策略

```yaml
rollback_policy:
  enabled: true
  conditions:
    - metric: error_rate
      threshold: 5%
      duration: 60s
      action: rollback
    - metric: p99_latency
      threshold: 3000ms
      duration: 120s
      action: rollback
    - metric: availability
      threshold: 99%
      duration: 180s
      action: alert
```

#### 5.5.3 回滚流程

```
┌─────────────────┐
│  监控系统检测    │
└────────┬────────┘
         │ 异常
         ▼
┌─────────────────┐
│  触发回滚条件    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  更新灰度规则    │ ← 将流量切回稳定版本
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  通知相关人员    │ ← 发送告警通知
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  记录回滚日志    │
└─────────────────┘
```

#### 5.5.4 手动回滚接口

```http
POST /api/v1/gray/rollback
Content-Type: application/json

{
  "service_id": "user-service",
  "target_version": "v1",
  "reason": "error_rate_exceeded",
  "operator": "admin"
}
```

---

## 6. 灰度策略类型说明

### 6.1 策略类型总览

| 策略类型 | 适用场景 | 实现方式 | 优点 | 缺点 |
|----------|----------|----------|------|------|
| 用户白名单 | 内部测试、VIP用户 | 用户ID列表匹配 | 精准控制、风险低 | 覆盖面小 |
| IP白名单 | 办公网络测试 | IP列表/CIDR匹配 | 简单易用 | 仅适用于固定IP场景 |
| Header路由 | A/B测试、特性开关 | HTTP Header匹配 | 灵活可控 | 需客户端配合 |
| 权重分流 | 全量灰度、逐步放量 | 随机权重分配 | 流量均匀分布 | 无法精准控制 |
| 业务属性 | 按租户、地区灰度 | 业务属性匹配 | 业务相关性强 | 需业务数据支持 |

### 6.2 用户白名单策略

#### 适用场景
- 内部员工测试新功能
- VIP用户体验新版本
- 特定用户群体的功能验证

#### 配置示例

```yaml
- rule_id: user-whitelist
  enabled: true
  description: "内部用户白名单灰度"
  match_conditions:
    - type: user_id
      operator: in
      values: ["user001", "user002", "user003"]
  target_version: v2
  priority: 100
```

#### 实现要点
- 支持动态更新用户列表
- 支持从外部系统（如用户中心）同步白名单
- 用户ID可从 JWT Token 或 Session 中提取

### 6.3 IP白名单策略

#### 适用场景
- 办公网络内部测试
- 特定地区用户灰度
- 安全测试环境验证

#### 配置示例

```yaml
- rule_id: ip-whitelist
  enabled: true
  description: "办公网络IP灰度"
  match_conditions:
    - type: ip
      operator: cidr
      cidr: "192.168.0.0/16"
  target_version: v2
  priority: 90
```

#### 实现要点
- 支持 IPv4 和 IPv6
- 支持单个IP、IP列表、CIDR网段
- 注意获取真实IP（考虑代理、负载均衡场景）

### 6.4 Header路由策略

#### 适用场景
- A/B测试不同功能版本
- 特性开关控制
- 客户端版本控制

#### 配置示例

```yaml
- rule_id: header-route
  enabled: true
  description: "Header路由灰度"
  match_conditions:
    - type: header
      key: X-Client-Version
      operator: regex
      pattern: "^2\\..*"
    - type: header
      key: X-Feature-Flag
      operator: eq
      value: "new-ui"
  target_version: v2
  priority: 80
```

#### 实现要点
- 支持多Header组合匹配
- 支持正则表达式匹配
- 可与客户端版本号结合使用

### 6.5 权重分流策略

#### 适用场景
- 全量灰度发布
- 逐步放量验证
- 压力测试

#### 配置示例

```yaml
- rule_id: weight-split
  enabled: true
  description: "权重分流灰度"
  match_conditions:
    - type: weight
      versions:
        - version: v1
          weight: 90
        - version: v2
          weight: 10
  target_version: auto
  priority: 1
```

#### 实现要点
- 使用一致性哈希或随机算法
- 支持多版本权重配置
- 权重总和应为100

#### 放量计划示例

| 阶段 | V1权重 | V2权重 | 持续时间 | 验证内容 |
|------|--------|--------|----------|----------|
| 阶段1 | 99% | 1% | 24小时 | 基本功能验证 |
| 阶段2 | 95% | 5% | 24小时 | 功能稳定性 |
| 阶段3 | 90% | 10% | 48小时 | 性能指标 |
| 阶段4 | 50% | 50% | 24小时 | 全量验证 |
| 阶段5 | 0% | 100% | - | 灰度完成 |

### 6.6 业务属性策略

#### 适用场景
- 按租户灰度（SaaS场景）
- 按地区灰度
- 按渠道灰度
- 按用户等级灰度

#### 配置示例

```yaml
- rule_id: tenant-gray
  enabled: true
  description: "租户灰度策略"
  match_conditions:
    - type: business
      attribute: tenant_id
      operator: in
      values: ["tenant001", "tenant002"]
  target_version: v2
  priority: 70
```

#### 实现要点
- 业务属性从请求上下文或用户信息中获取
- 支持多属性组合匹配
- 可与外部业务系统集成

---

## 7. 最佳实践

### 7.1 灰度发布流程建议

1. **小范围验证**：先使用白名单策略进行内部测试
2. **逐步放量**：使用权重策略逐步增加灰度流量
3. **监控告警**：配置完善的监控和告警机制
4. **快速回滚**：确保回滚机制可用且高效
5. **全量发布**：灰度验证通过后，全量切换到新版本

### 7.2 规则配置建议

- 保持规则简洁，避免过于复杂的组合条件
- 合理设置优先级，确保重要规则优先匹配
- 配置默认规则作为兜底策略
- 定期清理无效或过期的规则

### 7.3 监控指标建议

- 错误率：建议阈值 < 1%
- P99 延迟：建议不超过基线的 1.5 倍
- 可用性：建议 > 99.9%
- 业务指标：根据具体业务设定

### 7.4 安全建议

- 灰度规则变更需要审批流程
- 记录所有灰度操作日志
- 敏感功能灰度需要额外权限控制
- 灰度版本数据隔离

---

## 附录

### A. 灰度规则完整配置示例

```yaml
gray_config:
  global:
    enabled: true
    default_version: v1
    
  services:
    - service_id: user-service
      enabled: true
      rules:
        - rule_id: internal-test
          enabled: true
          description: "内部测试用户灰度"
          match_conditions:
            - type: user_id
              operator: in
              values: ["internal_001", "internal_002"]
          target_version: v2
          priority: 100
          
        - rule_id: office-ip
          enabled: true
          description: "办公网络灰度"
          match_conditions:
            - type: ip
              operator: cidr
              cidr: "10.0.0.0/8"
          target_version: v2
          priority: 90
          
        - rule_id: weight-gray
          enabled: true
          description: "权重灰度"
          match_conditions:
            - type: weight
              versions:
                - version: v1
                  weight: 90
                - version: v2
                  weight: 10
          target_version: auto
          priority: 1
          
    - service_id: order-service
      enabled: true
      rules:
        - rule_id: tenant-gray
          enabled: true
          description: "租户灰度"
          match_conditions:
            - type: business
              attribute: tenant_id
              operator: in
              values: ["tenant_vip_001"]
          target_version: v2
          priority: 100
          
  rollback_policy:
    enabled: true
    conditions:
      - metric: error_rate
        threshold: 5%
        duration: 60s
        action: rollback
      - metric: p99_latency
        threshold: 3000ms
        duration: 120s
        action: rollback
```

### B. 相关接口设计

#### B.1 灰度规则管理接口

```http
# 创建灰度规则
POST /api/v1/gray/rules
Content-Type: application/json

{
  "service_id": "user-service",
  "rule": {
    "rule_id": "user-gray-001",
    "enabled": true,
    "description": "用户灰度规则",
    "match_conditions": [...],
    "target_version": "v2",
    "priority": 100
  }
}

# 更新灰度规则
PUT /api/v1/gray/rules/{rule_id}

# 删除灰度规则
DELETE /api/v1/gray/rules/{rule_id}

# 查询灰度规则
GET /api/v1/gray/rules?service_id=user-service

# 启用/禁用规则
PATCH /api/v1/gray/rules/{rule_id}/toggle
```

#### B.2 灰度状态查询接口

```http
# 查询服务灰度状态
GET /api/v1/gray/status?service_id=user-service

Response:
{
  "service_id": "user-service",
  "versions": {
    "v1": {
      "instance_count": 5,
      "traffic_percentage": 90,
      "status": "stable"
    },
    "v2": {
      "instance_count": 2,
      "traffic_percentage": 10,
      "status": "gray"
    }
  },
  "rules": [...],
  "metrics": {
    "error_rate": 0.5,
    "p99_latency": 200,
    "availability": 99.95
  }
}
```

#### B.3 灰度回滚接口

```http
# 手动回滚
POST /api/v1/gray/rollback
Content-Type: application/json

{
  "service_id": "user-service",
  "target_version": "v1",
  "reason": "manual_rollback",
  "operator": "admin"
}

# 紧急回滚（所有服务）
POST /api/v1/gray/rollback/all
Content-Type: application/json

{
  "reason": "emergency_rollback",
  "operator": "admin"
}
```
