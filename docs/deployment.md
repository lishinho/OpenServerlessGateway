# OpenServerlessGateway 部署指南

本文档详细介绍 OpenServerlessGateway (OSG) 的部署方式和技术栈依赖。

---

## 一、部署方式概览

OSG 支持多种部署方式，适配不同企业的基础设施选型：

| 部署方式 | 适用场景 | 推荐指数 |
|----------|----------|----------|
| 容器化部署（Docker/K8s） | 云原生环境、生产环境 | ⭐⭐⭐⭐⭐ |
| Serverless 平台部署 | 弹性伸缩需求、按需付费 | ⭐⭐⭐⭐ |
| 传统虚拟机部署 | 传统 IDC、过渡期环境 | ⭐⭐⭐ |

---

## 二、容器化部署（推荐）

### 2.1 Docker 部署

#### 2.1.1 快速启动

```bash
# 拉取官方镜像
docker pull osg/open-serverless-gateway:latest

# 启动网关实例
docker run -d \
  --name osg-gateway \
  -p 8080:8080 \
  -p 9090:9090 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e NACOS_SERVER_ADDR=nacos:8848 \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  osg/open-serverless-gateway:latest
```

#### 2.1.2 Docker Compose 部署

```yaml
version: '3.8'

services:
  osg-gateway:
    image: osg/open-serverless-gateway:latest
    container_name: osg-gateway
    ports:
      - "8080:8080"
      - "9090:9090"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - NACOS_SERVER_ADDR=nacos:8848
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - MYSQL_HOST=mysql
      - MYSQL_PORT=3306
    depends_on:
      - nacos
      - redis
      - mysql
    networks:
      - osg-network

  nacos:
    image: nacos/nacos-server:latest
    container_name: nacos
    environment:
      - MODE=standalone
    ports:
      - "8848:8848"
    networks:
      - osg-network

  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "6379:6379"
    networks:
      - osg-network

  mysql:
    image: mysql:8.0
    container_name: mysql
    environment:
      - MYSQL_ROOT_PASSWORD=root123
      - MYSQL_DATABASE=osg
    ports:
      - "3306:3306"
    networks:
      - osg-network

networks:
  osg-network:
    driver: bridge
```

```bash
# 启动所有服务
docker-compose up -d
```

### 2.2 Kubernetes 部署

#### 2.2.1 Namespace 创建

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: osg-system
```

#### 2.2.2 ConfigMap 配置

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: osg-config
  namespace: osg-system
data:
  application.yml: |
    spring:
      profiles:
        active: k8s
      cloud:
        nacos:
          discovery:
            server-addr: nacos-service:8848
          config:
            server-addr: nacos-service:8848
    osg:
      redis:
        host: redis-service
        port: 6379
      mysql:
        host: mysql-service
        port: 3306
```

#### 2.2.3 Deployment 部署

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: osg-gateway
  namespace: osg-system
spec:
  replicas: 3
  selector:
    matchLabels:
      app: osg-gateway
  template:
    metadata:
      labels:
        app: osg-gateway
    spec:
      containers:
      - name: osg-gateway
        image: osg/open-serverless-gateway:latest
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 9090
          name: management
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx1024m"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 9090
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 9090
          initialDelaySeconds: 10
          periodSeconds: 5
        volumeMounts:
        - name: config-volume
          mountPath: /config
      volumes:
      - name: config-volume
        configMap:
          name: osg-config
```

#### 2.2.4 Service 服务

```yaml
apiVersion: v1
kind: Service
metadata:
  name: osg-gateway-service
  namespace: osg-system
spec:
  type: LoadBalancer
  selector:
    app: osg-gateway
  ports:
  - name: http
    port: 80
    targetPort: 8080
  - name: management
    port: 9090
    targetPort: 9090
```

#### 2.2.5 HPA 自动扩缩容

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: osg-gateway-hpa
  namespace: osg-system
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: osg-gateway
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

#### 2.2.6 部署命令

```bash
# 应用所有配置
kubectl apply -f osg-namespace.yaml
kubectl apply -f osg-configmap.yaml
kubectl apply -f osg-deployment.yaml
kubectl apply -f osg-service.yaml
kubectl apply -f osg-hpa.yaml

# 查看部署状态
kubectl get pods -n osg-system
kubectl get services -n osg-system
```

---

## 三、Serverless 平台部署

### 3.1 Knative 部署

#### 3.1.1 Knative Service 配置

```yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: osg-gateway
  namespace: osg-system
spec:
  template:
    metadata:
      annotations:
        autoscaling.knative.dev/minScale: "1"
        autoscaling.knative.dev/maxScale: "10"
        autoscaling.knative.dev/target: "100"
    spec:
      containers:
      - image: osg/open-serverless-gateway:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "knative"
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

```bash
# 部署到 Knative
kubectl apply -f osg-knative-service.yaml
```

### 3.2 OpenFaaS 部署

#### 3.2.1 OpenFaaS Function 配置

```yaml
version: 1.0
provider:
  name: openfaas
  gateway: http://gateway.openfaas:8080

functions:
  osg-gateway:
    lang: java11
    handler: ./osg-gateway
    image: osg/open-serverless-gateway:latest
    environment:
      SPRING_PROFILES_ACTIVE: openfaas
    limits:
      memory: 512Mi
      cpu: 500m
    requests:
      memory: 256Mi
      cpu: 200m
```

```bash
# 部署到 OpenFaaS
faas-cli deploy -f osg-openfaas.yml
```

---

## 四、传统虚拟机部署

### 4.1 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 8+ 或 11+ |
| Redis | 5.0+ |
| MySQL | 5.7+ 或 PostgreSQL 12+ |
| Nacos | 2.0+ |

### 4.2 部署步骤

#### 4.2.1 下载安装包

```bash
# 下载最新版本
wget https://github.com/osg/open-serverless-gateway/releases/download/v1.0.0/osg-gateway-1.0.0.tar.gz

# 解压
tar -xzf osg-gateway-1.0.0.tar.gz
cd osg-gateway-1.0.0
```

#### 4.2.2 配置文件修改

```yaml
# config/application.yml
spring:
  profiles:
    active: prod
  cloud:
    nacos:
      discovery:
        server-addr: 192.168.1.100:8848
      config:
        server-addr: 192.168.1.100:8848

osg:
  redis:
    host: 192.168.1.101
    port: 6379
    password: your-password
  mysql:
    host: 192.168.1.102
    port: 3306
    database: osg
    username: osg
    password: your-password
```

#### 4.2.3 启动服务

```bash
# 前台启动
java -jar osg-gateway.jar

# 后台启动
nohup java -jar osg-gateway.jar > osg.log 2>&1 &

# 指定配置文件启动
java -jar osg-gateway.jar --spring.config.location=config/application.yml
```

#### 4.2.4 Systemd 服务配置

```ini
# /etc/systemd/system/osg-gateway.service
[Unit]
Description=OpenServerlessGateway
After=network.target

[Service]
Type=simple
User=osg
WorkingDirectory=/opt/osg-gateway
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar osg-gateway.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# 启用并启动服务
systemctl enable osg-gateway
systemctl start osg-gateway
systemctl status osg-gateway
```

---

## 五、技术栈依赖

### 5.1 核心技术栈

OSG 采用 Java 语言开发，所有依赖均为开源主流组件，无闭源依赖：

| 技术栈类型 | 核心组件 | 说明 |
|------------|----------|------|
| 核心框架 | Spring Boot / Spring Cloud Starter | 轻量级、易扩展 |
| 配置中心 | Nacos / Apollo / ETCD | 可替换 |
| 注册中心 | Eureka / Nacos / Consul | 可替换 |
| 存储组件 | Redis | 限流/会话/缓存 |
| 数据库 | MySQL / PostgreSQL | 配置/元数据存储 |
| 监控告警 | Prometheus / Grafana | 指标监控 |
| 日志系统 | ELK (Elasticsearch/Logstash/Kibana) | 日志采集分析 |
| 告警系统 | AlertManager | 告警通知 |
| 链路追踪 | SkyWalking / Pinpoint / Zipkin | 可替换 |
| 容器化 | Docker / K8s / Knative / OpenFaaS | 容器编排 |
| 构建工具 | Maven / Gradle | 项目构建 |

### 5.2 组件版本建议

| 组件 | 推荐版本 |
|------|----------|
| Spring Boot | 2.7.x / 3.x |
| Spring Cloud | 2021.x / 2022.x |
| Nacos | 2.2.x |
| Redis | 7.x |
| MySQL | 8.0 |
| Prometheus | 2.x |
| Grafana | 10.x |
| SkyWalking | 9.x |
| Docker | 24.x |
| Kubernetes | 1.25+ |

### 5.3 组件可替换性

OSG 的底层组件均采用**可替换、可扩展**设计：

```
┌─────────────────────────────────────────────────────────────┐
│                      OSG 网关核心                            │
└─────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
   ┌─────────┐        ┌─────────┐        ┌─────────┐
   │ 配置中心 │        │ 注册中心 │        │ 存储组件 │
   ├─────────┤        ├─────────┤        ├─────────┤
   │ Nacos   │        │ Eureka  │        │ Redis   │
   │ Apollo  │        │ Nacos   │        │         │
   │ ETCD    │        │ Consul  │        │         │
   └─────────┘        └─────────┘        └─────────┘
```

---

## 六、网络与端口配置

### 6.1 默认端口

| 端口 | 用途 |
|------|------|
| 8080 | HTTP 服务端口 |
| 9090 | 管理端口（健康检查、指标暴露） |

### 6.2 防火墙配置

```bash
# 开放 HTTP 端口
firewall-cmd --add-port=8080/tcp --permanent

# 开放管理端口
firewall-cmd --add-port=9090/tcp --permanent

# 重载防火墙
firewall-cmd --reload
```

---

## 七、健康检查与监控

### 7.1 健康检查端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 综合健康状态 |
| `/actuator/health/liveness` | 存活探针 |
| `/actuator/health/readiness` | 就绪探针 |

### 7.2 监控指标端点

| 端点 | 说明 |
|------|------|
| `/actuator/prometheus` | Prometheus 指标 |
| `/actuator/metrics` | Metrics 指标 |

### 7.3 Prometheus 配置

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'osg-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['osg-gateway:9090']
```

---

## 八、故障排查

### 8.1 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 启动失败 | 配置中心连接失败 | 检查 Nacos 地址和网络 |
| 限流不生效 | Redis 连接失败 | 检查 Redis 配置和连接 |
| 服务发现失败 | 注册中心连接失败 | 检查注册中心配置 |
| 内存溢出 | JVM 内存不足 | 调整 JVM 参数 |

### 8.2 日志查看

```bash
# Docker 日志
docker logs osg-gateway

# K8s 日志
kubectl logs -f deployment/osg-gateway -n osg-system

# 虚拟机日志
tail -f /opt/osg-gateway/logs/osg.log
```

---

## 九、安全配置

### 9.1 敏感信息保护

- 使用环境变量传递敏感配置
- 使用配置中心加密功能
- 定期轮换密码和 Token

### 9.2 网络安全

- 启用 HTTPS
- 配置 IP 白名单
- 启用访问日志审计

---

## 十、升级与回滚

### 10.1 滚动升级

```bash
# K8s 滚动升级
kubectl set image deployment/osg-gateway osg-gateway=osg/open-serverless-gateway:v1.1.0 -n osg-system

# 查看升级状态
kubectl rollout status deployment/osg-gateway -n osg-system
```

### 10.2 回滚操作

```bash
# K8s 回滚到上一版本
kubectl rollout undo deployment/osg-gateway -n osg-system

# 回滚到指定版本
kubectl rollout undo deployment/osg-gateway --to-revision=2 -n osg-system
```
