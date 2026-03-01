# OpenServerlessGateway 文档中心

欢迎来到 OpenServerlessGateway (OSG) 文档中心！本页面提供完整的文档导航索引，帮助您快速找到所需信息。

---

## 文档目录结构概览

```
docs/
├── README.md                 # 文档导航索引（本页面）
├── architecture.md           # 架构设计文档
├── features.md               # 核心特性文档
├── getting-started.md        # 快速入门指南
├── deployment.md             # 部署指南
├── roadmap.md                # 路线图
├── community.md              # 社区参与指南
├── design/                   # 核心设计文档
│   ├── rate-limiting.md      # 限流设计文档
│   ├── authentication.md     # 鉴权架构设计文档
│   ├── http-to-rpc.md        # HTTP转RPC转换引擎设计
│   └── gray-release.md       # 灰度发布方案设计
└── modules/                  # 模块文档
    ├── traffic-control.md    # 流量管控模块
    ├── authentication.md     # 鉴权模块
    ├── protocol-conversion.md # 协议转换模块
    └── dev-efficiency.md     # 研发提效模块
```

---

## 快速导航

### 新手入门

| 文档 | 说明 |
|------|------|
| [快速入门指南](getting-started.md) | 了解如何快速安装和运行 OSG |
| [架构设计文档](architecture.md) | 了解 OSG 的整体架构设计理念 |
| [核心特性文档](features.md) | 了解 OSG 的核心特性和能力 |

### 部署运维

| 文档 | 说明 |
|------|------|
| [部署指南](deployment.md) | 详细的部署方案和配置说明 |

### 社区参与

| 文档 | 说明 |
|------|------|
| [社区参与指南](community.md) | 了解如何参与 OSG 社区贡献 |
| [路线图](roadmap.md) | 了解 OSG 的版本规划和发展方向 |

---

## 核心设计文档

核心设计文档详细描述了 OSG 各核心能力的设计思路和实现方案。

### 流量管控

| 文档 | 说明 |
|------|------|
| [限流设计文档](design/rate-limiting.md) | 限流架构设计、算法选择、分布式限流实现方案 |

**核心内容**：
- 限流决策引擎架构
- 多维度限流（接口级/IP级/用户级/业务级/组合级）
- 分布式限流实现（Redis Lua 脚本）
- 限流算法对比（令牌桶、滑动窗口、漏桶、并发限流）
- 配置示例和最佳实践

### 鉴权安全

| 文档 | 说明 |
|------|------|
| [鉴权架构设计文档](design/authentication.md) | 鉴权架构、RBAC/ABAC权限模型、会话管理方案 |

**核心内容**：
- 鉴权处理链设计（Token解析、身份认证、权限校验）
- RBAC 权限模型（角色、权限、继承）
- ABAC 权限模型（属性、策略、条件表达式）
- 上下文透传设计（HTTP Header标准、签名机制）
- JWT/Session 会话管理方案

### 协议转换

| 文档 | 说明 |
|------|------|
| [HTTP转RPC转换引擎设计](design/http-to-rpc.md) | HTTP到RPC的自动转换、泛化调用、服务发现集成 |

**核心内容**：
- HTTP-RPC 转换引擎架构
- API 映射配置规范（YAML格式）
- 泛化调用核心实现（Dubbo示例）
- 参数转换规则和校验
- 服务发现集成方案（Nacos、Zookeeper）

### 灰度发布

| 文档 | 说明 |
|------|------|
| [灰度发布方案设计](design/gray-release.md) | 灰度决策引擎、规则配置、网关层和服务层灰度方案 |

**核心内容**：
- 灰度决策引擎架构
- 灰度规则配置规范
- 网关层灰度方案
- 业务服务层灰度方案
- 灰度策略类型（用户白名单、IP白名单、Header路由、权重分流、业务属性）

---

## 模块文档

模块文档详细描述了 OSG 各功能模块的使用方法和配置说明。

### 流量管控模块

| 文档 | 说明 |
|------|------|
| [流量管控模块](modules/traffic-control.md) | 限流、降级、重试、灰度引流、流量回放、接口防刷、并发控制 |

**核心功能**：
- 细粒度限流（IP/接口/用户/业务维度）
- 自动降级（接口级/业务级）
- 智能重试（可配置重试策略）
- 灰度引流（多维度灰度规则）
- 流量回放（请求录制和回放）
- 接口防刷（IP/用户防刷）
- 并发控制（接口级线程池）

### 鉴权模块

| 文档 | 说明 |
|------|------|
| [鉴权模块](modules/authentication.md) | RBAC/ABAC权限模型、Session治理、上下文透传 |

**核心功能**：
- 多权限模型支持（RBAC/ABAC）
- Session 治理（分布式Session管理）
- 权限点映射（菜单/按钮/URI权限）
- 上下文透传（全链路鉴权信息透传）
- 免登录鉴权（测试接口/内部接口）
- 动态 Token（JWT + Redis Token管理）

### 协议转换模块

| 文档 | 说明 |
|------|------|
| [协议转换模块](modules/protocol-conversion.md) | 多协议互转、泛化调用、API注册与映射 |

**核心功能**：
- 多协议互转（HTTP/HTTPS ↔ Dubbo/SOFA/gRPC）
- 泛化调用（零代码接入后端RPC服务）
- API 注册（HTTP接口与后端服务一键映射）
- 自定义编排（多后端服务接口编排）
- 报文标准化（请求/返回报文标准化封装）

### 研发提效模块

| 文档 | 说明 |
|------|------|
| [研发提效模块](modules/dev-efficiency.md) | 自动化接口文档、Mock调试、CI/CD流水线对接 |

**核心功能**：
- 自动化接口文档（Swagger/OpenAPI 3.0）
- Mock 调试（接口级Mock配置）
- CI/CD 对接（Jenkins/GitLab CI流水线）
- 统一错误码（前后端统一错误码体系）
- 全链路排查（TraceID全链路日志查询）
- 文件传输（上传下载标准化接口）

---

## 其他文档

### 项目概览

| 文档 | 说明 |
|------|------|
| [架构设计文档](architecture.md) | 项目背景、核心目标、整体架构、技术栈选型 |
| [核心特性文档](features.md) | 无状态部署、热部署、插件化扩展、低代码接入、云原生适配 |

### 发展规划

| 文档 | 说明 |
|------|------|
| [路线图](roadmap.md) | 版本规划、扩展能力规划、技术演进路线 |

**版本规划**：
- **V1.0** - 核心功能开源（限流、鉴权、协议转换、灰度发布）
- **V1.1-V1.2** - 功能增强（智能重试、流量回放、接口防刷、自动降级、并发控制）
- **V2.0** - Serverless深度适配
- **V3.0+** - AI驱动智能化

### 社区资源

| 文档 | 说明 |
|------|------|
| [社区参与指南](community.md) | 如何参与社区、贡献代码、报告问题 |

---

## 文档版本说明

| 版本 | 更新日期 | 说明 |
|------|----------|------|
| V1.0 | 2026.03 | 初始版本，包含核心设计文档和模块文档 |

---

## 反馈与建议

如果您发现文档有问题或有改进建议，欢迎通过以下方式反馈：

- 📧 邮件：osg@example.com
- 💬 GitHub Issues：[提交 Issue](https://github.com/osg/open-serverless-gateway/issues)
- 📖 GitHub Discussions：[参与讨论](https://github.com/osg/open-serverless-gateway/discussions)
