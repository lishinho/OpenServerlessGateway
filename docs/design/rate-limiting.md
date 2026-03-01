# 限流设计文档

## 1. 限流架构设计

### 1.1 整体架构

限流系统采用分层架构设计，包含决策引擎、算法实现层和存储层三个核心层次，实现高内聚、低耦合的限流能力。

```
┌─────────────────────────────────────────────────────────────┐
│                      限流决策引擎                            │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ 规则解析器   │  │ 算法选择器   │  │ 策略执行器   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │              限流算法实现层（SPI插件化）              │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │   │
│  │  │令牌桶   │ │漏桶     │ │滑动窗口 │ │并发限流 │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │              分布式计数器存储层                       │   │
│  │  ┌─────────────┐  ┌─────────────┐                   │   │
│  │  │ Redis Lua   │  │ 本地缓存    │（二级缓存）        │   │
│  │  └─────────────┘  └─────────────┘                   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 决策引擎

决策引擎是限流系统的核心控制层，负责接收请求、解析规则、选择算法并执行限流策略。

#### 1.2.1 规则解析器

规则解析器负责解析和加载限流配置规则，支持从多种数据源加载配置：

- **配置中心**：支持从 Nacos、Apollo 等配置中心动态加载限流规则
- **数据库**：从 MySQL 等数据库加载持久化的限流配置
- **本地文件**：支持本地 YAML/JSON 配置文件

规则解析器支持热更新，当配置发生变化时，能够实时生效，无需重启服务。

#### 1.2.2 算法选择器

算法选择器根据限流规则配置，选择合适的限流算法：

- 根据场景自动推荐最优算法
- 支持自定义算法优先级
- 支持算法降级策略

#### 1.2.3 策略执行器

策略执行器负责执行限流决策，并处理限流后的行为：

- **通过**：请求正常放行
- **拒绝**：返回限流错误响应（HTTP 429）
- **降级**：执行降级逻辑，返回默认响应
- **排队**：将请求加入等待队列（适用于漏桶算法）

### 1.3 算法实现层

算法实现层采用 SPI（Service Provider Interface）插件化设计，支持灵活扩展：

- **令牌桶算法**：适用于突发流量场景
- **漏桶算法**：适用于平滑流量场景
- **滑动窗口算法**：适用于精确限流场景
- **并发限流**：适用于控制并发数场景

### 1.4 存储层

存储层提供分布式计数器能力，支持多级缓存：

- **Redis Lua**：分布式限流的核心存储，保证原子性操作
- **本地缓存**：二级缓存，减少 Redis 访问压力，提升性能

---

## 2. 限流维度设计

### 2.1 维度概述

限流维度定义了限流的粒度和范围，支持从多个角度对请求进行限流控制。

| 维度 | Key 设计 | 说明 |
|------|----------|------|
| 接口级 | `limit:api:{api_id}` | 针对单个接口的总限流 |
| IP级 | `limit:ip:{api_id}:{ip}` | 针对单个IP的限流 |
| 用户级 | `limit:user:{api_id}:{user_id}` | 针对单个用户的限流 |
| 业务级 | `limit:biz:{api_id}:{biz_key}` | 针对业务属性的限流 |
| 组合级 | `limit:combo:{api_id}:{hash(ip+user+biz)}` | 多维度组合限流 |

### 2.2 接口级限流

接口级限流是对单个 API 接口的总体限流，不考虑请求来源。

**适用场景**：
- 保护后端服务不被过载
- 控制接口总体吞吐量
- 服务容量规划

**配置示例**：

```yaml
rate_limit:
  api_id: "user-service-getUser"
  dimension: "api"
  algorithm: "token_bucket"
  capacity: 1000
  rate: 100
```

### 2.3 IP级限流

IP级限流针对单个客户端IP地址进行限流，防止恶意攻击或过度使用。

**适用场景**：
- 防止 DDoS 攻击
- 防止单个用户过度调用
- 公开 API 的公平访问控制

**配置示例**：

```yaml
rate_limit:
  api_id: "public-api-weather"
  dimension: "ip"
  algorithm: "sliding_window"
  window_size: 60
  threshold: 100
```

### 2.4 用户级限流

用户级限流针对已认证用户进行限流，常用于 API 配额管理。

**适用场景**：
- API 套餐配额管理
- VIP 用户差异化限流
- 用户行为控制

**配置示例**：

```yaml
rate_limit:
  api_id: "payment-service-createOrder"
  dimension: "user"
  algorithm: "token_bucket"
  capacity: 100
  rate: 10
  user_tiers:
    - tier: "free"
      capacity: 50
      rate: 5
    - tier: "premium"
      capacity: 500
      rate: 50
    - tier: "enterprise"
      capacity: 5000
      rate: 500
```

### 2.5 业务级限流

业务级限流根据业务属性进行限流，如商户ID、商品ID等。

**适用场景**：
- 多租户限流
- 热点商品限流
- 商户配额管理

**配置示例**：

```yaml
rate_limit:
  api_id: "order-service-createOrder"
  dimension: "biz"
  biz_key_extractor: "merchant_id"
  algorithm: "token_bucket"
  capacity: 1000
  rate: 100
```

### 2.6 组合级限流

组合级限流支持多个维度的组合，实现更精细的限流控制。

**适用场景**：
- 复杂业务场景
- 多维度协同限流
- 精细化流量控制

**配置示例**：

```yaml
rate_limit:
  api_id: "flash-sale-order"
  dimension: "combo"
  combo_keys:
    - "ip"
    - "user_id"
    - "product_id"
  algorithm: "sliding_window"
  window_size: 1
  threshold: 1
```

---

## 3. 分布式限流实现方案

### 3.1 Redis Lua 脚本实现

分布式限流的核心是保证限流操作的原子性，Redis Lua 脚本可以确保多个 Redis 命令在单个原子操作中执行。

### 3.2 令牌桶算法 Lua 脚本

```lua
-- Redis Lua 脚本：令牌桶限流
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local last_refill = redis.call('hget', key, 'last_refill')
local tokens = redis.call('hget', key, 'tokens')

if last_refill == false then
    last_refill = now
    tokens = capacity
end

local elapsed = now - last_refill
local refill_tokens = math.floor(elapsed * rate)
tokens = math.min(capacity, tokens + refill_tokens)

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call('hset', key, 'tokens', tokens)
redis.call('hset', key, 'last_refill', now)
redis.call('expire', key, 3600)

return {allowed, tokens}
```

**参数说明**：

| 参数 | 类型 | 说明 |
|------|------|------|
| KEYS[1] | string | 限流 Key |
| ARGV[1] | number | 桶容量 |
| ARGV[2] | number | 令牌生成速率（个/秒） |
| ARGV[3] | number | 请求令牌数 |
| ARGV[4] | number | 当前时间戳（毫秒） |

**返回值说明**：

| 返回值 | 类型 | 说明 |
|--------|------|------|
| allowed | number | 是否通过（1=通过，0=拒绝） |
| tokens | number | 剩余令牌数 |

### 3.3 滑动窗口算法 Lua 脚本

```lua
-- Redis Lua 脚本：滑动窗口限流
local key = KEYS[1]
local window_size = tonumber(ARGV[1])
local threshold = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

redis.call('zremrangebyscore', key, 0, now - window_size * 1000)

local count = redis.call('zcard', key)

local allowed = 0
if count < threshold then
    redis.call('zadd', key, now, now .. '-' .. math.random())
    allowed = 1
end

redis.call('expire', key, window_size + 1)

return {allowed, count + 1}
```

**参数说明**：

| 参数 | 类型 | 说明 |
|------|------|------|
| KEYS[1] | string | 限流 Key |
| ARGV[1] | number | 窗口大小（秒） |
| ARGV[2] | number | 阈值 |
| ARGV[3] | number | 当前时间戳（毫秒） |

### 3.4 并发限流 Lua 脚本

```lua
-- Redis Lua 脚本：并发限流
local key = KEYS[1]
local max_concurrent = tonumber(ARGV[1])
local request_id = ARGV[2]
local ttl = tonumber(ARGV[3])

local current = redis.call('scard', key)

local allowed = 0
if current < max_concurrent then
    redis.call('sadd', key, request_id)
    redis.call('expire', key, ttl)
    allowed = 1
end

return {allowed, current + 1}
```

**参数说明**：

| 参数 | 类型 | 说明 |
|------|------|------|
| KEYS[1] | string | 限流 Key |
| ARGV[1] | number | 最大并发数 |
| ARGV[2] | string | 请求唯一标识 |
| ARGV[3] | number | 过期时间（秒） |

### 3.5 释放并发限流 Lua 脚本

```lua
-- Redis Lua 脚本：释放并发限流
local key = KEYS[1]
local request_id = ARGV[1]

redis.call('srem', key, request_id)

local current = redis.call('scard', key)

return current
```

---

## 4. 限流算法选择说明

### 4.1 算法对比

| 算法 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| 令牌桶 | 允许突发流量、实现简单 | 需要维护令牌状态 | API 网关、突发流量场景 |
| 漏桶 | 输出流量平滑、保护后端 | 无法应对突发流量 | 流量整形、保护脆弱服务 |
| 滑动窗口 | 限流精确、无突发流量 | 内存占用较高 | 精确限流、配额管理 |
| 并发限流 | 控制真实并发数 | 需要释放机制 | 数据库连接池、线程池 |

### 4.2 令牌桶算法

**原理**：
- 系统以固定速率向桶中放入令牌
- 请求到达时，从桶中获取令牌
- 桶中无令牌则拒绝请求
- 桶满时，新令牌被丢弃

**特点**：
- 允许一定程度的突发流量
- 可以限制平均速率
- 实现相对简单

**适用场景**：
- API 网关限流
- 允许突发流量的业务
- 需要平滑限流的场景

### 4.3 滑动窗口算法

**原理**：
- 将时间窗口划分为多个小格
- 请求到达时，记录到对应小格
- 计算窗口内的请求数量
- 超过阈值则拒绝

**特点**：
- 限流精确，无突发流量
- 可以精确控制时间窗口内的请求数
- 内存占用相对较高

**适用场景**：
- 精确限流要求
- API 配额管理
- 防刷限流

### 4.4 并发限流算法

**原理**：
- 维护当前并发请求数
- 请求开始时，计数器加一
- 请求结束时，计数器减一
- 超过阈值则拒绝

**特点**：
- 控制真实并发数
- 需要请求结束释放
- 保护后端资源

**适用场景**：
- 数据库连接池限流
- 线程池限流
- 保护有限资源

### 4.5 算法选择建议

```
┌─────────────────────────────────────────────────────────────┐
│                      算法选择决策树                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  是否需要控制并发数？                                        │
│       │                                                     │
│       ├── 是 ──→ 并发限流                                   │
│       │                                                     │
│       └── 否 ──→ 是否允许突发流量？                          │
│                     │                                       │
│                     ├── 是 ──→ 令牌桶                       │
│                     │                                       │
│                     └── 否 ──→ 是否需要流量平滑？            │
│                                   │                         │
│                                   ├── 是 ──→ 漏桶           │
│                                   │                         │
│                                   └── 否 ──→ 滑动窗口       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. 限流配置示例和场景说明

### 5.1 配置结构

```yaml
rate_limit:
  enabled: true
  default_algorithm: "token_bucket"
  storage:
    type: "redis"
    redis:
      host: "localhost"
      port: 6379
      database: 0
    local_cache:
      enabled: true
      size: 10000
      ttl: 60
  rules:
    - api_id: "user-service-getUser"
      dimension: "api"
      algorithm: "token_bucket"
      capacity: 1000
      rate: 100
      action: "reject"
      response:
        status_code: 429
        message: "请求过于频繁，请稍后重试"
```

### 5.2 场景一：API 网关全局限流

**需求**：对所有 API 进行全局限流，保护网关整体稳定性。

```yaml
rate_limit:
  rules:
    - api_id: "*"
      dimension: "api"
      algorithm: "token_bucket"
      capacity: 10000
      rate: 1000
      action: "reject"
```

### 5.3 场景二：公开 API 防刷

**需求**：公开 API 需要防止被恶意刷量，按 IP 进行限流。

```yaml
rate_limit:
  rules:
    - api_id: "public-api-*"
      dimension: "ip"
      algorithm: "sliding_window"
      window_size: 60
      threshold: 100
      action: "reject"
      response:
        status_code: 429
        headers:
          X-RateLimit-Limit: "100"
          X-RateLimit-Remaining: "${remaining}"
          X-RateLimit-Reset: "${reset_time}"
```

### 5.4 场景三：用户配额管理

**需求**：根据用户等级进行差异化限流。

```yaml
rate_limit:
  rules:
    - api_id: "api-service-*"
      dimension: "user"
      algorithm: "token_bucket"
      capacity: 100
      rate: 10
      user_tiers:
        - tier: "free"
          capacity: 50
          rate: 5
        - tier: "basic"
          capacity: 200
          rate: 20
        - tier: "pro"
          capacity: 1000
          rate: 100
        - tier: "enterprise"
          capacity: 10000
          rate: 1000
```

### 5.5 场景四：秒杀活动限流

**需求**：秒杀活动需要严格控制流量，防止系统崩溃。

```yaml
rate_limit:
  rules:
    - api_id: "flash-sale-order"
      dimension: "combo"
      combo_keys:
        - "user_id"
        - "product_id"
      algorithm: "sliding_window"
      window_size: 1
      threshold: 1
      action: "reject"
      response:
        status_code: 429
        message: "您操作过于频繁，请稍后重试"
    - api_id: "flash-sale-order"
      dimension: "api"
      algorithm: "token_bucket"
      capacity: 10000
      rate: 5000
      action: "queue"
      queue:
        max_size: 1000
        timeout: 5000
```

### 5.6 场景五：数据库保护

**需求**：保护数据库连接池，控制并发查询数。

```yaml
rate_limit:
  rules:
    - api_id: "db-query-*"
      dimension: "api"
      algorithm: "concurrent"
      max_concurrent: 100
      timeout: 30000
      action: "reject"
      response:
        status_code: 503
        message: "服务繁忙，请稍后重试"
```

### 5.7 场景六：多租户限流

**需求**：SaaS 平台需要对不同租户进行独立限流。

```yaml
rate_limit:
  rules:
    - api_id: "saas-api-*"
      dimension: "biz"
      biz_key_extractor: "tenant_id"
      algorithm: "token_bucket"
      capacity: 1000
      rate: 100
      tenant_quotas:
        - tenant_id: "tenant_001"
          capacity: 5000
          rate: 500
        - tenant_id: "tenant_002"
          capacity: 2000
          rate: 200
```

### 5.8 场景七：熔断降级配合

**需求**：限流与熔断降级配合使用，提供多层保护。

```yaml
rate_limit:
  rules:
    - api_id: "payment-service-*"
      dimension: "api"
      algorithm: "token_bucket"
      capacity: 500
      rate: 50
      action: "degrade"
      degrade:
        enabled: true
        response:
          status_code: 200
          body: |
            {
              "code": "RATE_LIMITED",
              "message": "服务繁忙，使用降级响应",
              "data": null
            }
      circuit_breaker:
        enabled: true
        failure_threshold: 50
        success_threshold: 10
        timeout: 30000
```

### 5.9 响应头配置

限流响应应包含标准化的响应头，方便客户端了解限流状态：

| 响应头 | 说明 |
|--------|------|
| X-RateLimit-Limit | 限流阈值 |
| X-RateLimit-Remaining | 剩余配额 |
| X-RateLimit-Reset | 重置时间（Unix 时间戳） |
| X-RateLimit-Retry-After | 建议重试等待时间（秒） |

---

## 6. 最佳实践

### 6.1 限流阈值设置

1. **压测确定基线**：通过压力测试确定系统的最大处理能力
2. **留有余量**：限流阈值设置为系统最大处理能力的 70%-80%
3. **分级设置**：根据业务重要性设置不同级别的限流阈值
4. **动态调整**：根据系统负载动态调整限流阈值

### 6.2 监控告警

1. **限流次数监控**：监控被限流的请求数量和比例
2. **限流趋势分析**：分析限流趋势，及时发现异常
3. **告警配置**：限流比例超过阈值时触发告警

### 6.3 客户端配合

1. **响应处理**：客户端正确处理 429 响应
2. **重试策略**：实现指数退避重试机制
3. **本地限流**：客户端实现本地限流，减少服务端压力
