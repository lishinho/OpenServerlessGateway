# HTTP 到 RPC 转换引擎设计文档

## 1. 转换引擎架构设计

### 1.1 整体架构

HTTP-RPC 转换引擎是网关的核心组件，负责将 HTTP 请求转换为后端 RPC 调用，并将 RPC 结果转换为 HTTP 响应返回给客户端。

```
┌─────────────────────────────────────────────────────────────┐
│                    HTTP-RPC 转换引擎                         │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    元数据注册中心                     │   │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐       │   │
│  │  │ API映射表 │  │ 服务元数据 │  │ 参数映射  │       │   │
│  │  └───────────┘  └───────────┘  └───────────┘       │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    协议适配层（SPI）                  │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │   │
│  │  │ Dubbo   │ │ gRPC    │ │ SOFA    │ │ 自定义  │   │   │
│  │  │ Adapter │ │ Adapter │ │ Adapter │ │ Adapter │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    泛化调用引擎                       │   │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐       │   │
│  │  │ 服务发现  │  │ 负载均衡  │  │ 结果转换  │       │   │
│  │  └───────────┘  └───────────┘  └───────────┘       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 核心模块说明

#### 1.2.1 元数据注册中心

元数据注册中心是整个转换引擎的数据基础，负责存储和管理所有与转换相关的配置信息。

**API 映射表**
- 存储 HTTP 路径到 RPC 服务的映射关系
- 支持动态路由和路径参数解析
- 提供映射规则的版本管理

**服务元数据**
- RPC 服务接口定义信息
- 方法签名和参数类型信息
- 服务版本和分组信息

**参数映射**
- HTTP 参数到 RPC 参数的映射规则
- 参数类型转换配置
- 默认值和校验规则

#### 1.2.2 协议适配层（SPI）

协议适配层采用 SPI（Service Provider Interface）设计模式，支持多种 RPC 协议的灵活扩展。

**支持的协议**
- **Dubbo Adapter**: 阿里巴巴 Dubbo RPC 框架适配器
- **gRPC Adapter**: Google gRPC 协议适配器
- **SOFA Adapter**: 蚂蚁金服 SOFA-RPC 适配器
- **自定义 Adapter**: 支持用户自定义协议扩展

**适配器接口定义**

```java
public interface RpcAdapter {
    
    String getProtocol();
    
    void initialize(RpcAdapterConfig config);
    
    Object invoke(RpcInvocation invocation);
    
    void destroy();
}
```

#### 1.2.3 泛化调用引擎

泛化调用引擎是实际执行 RPC 调用的核心组件，无需依赖服务接口定义即可完成调用。

**服务发现**
- 集成注册中心（Nacos、Zookeeper 等）
- 动态感知服务实例变化
- 支持多注册中心配置

**负载均衡**
- 支持多种负载均衡策略：随机、轮询、加权、一致性哈希
- 支持自定义负载均衡算法
- 失败重试和故障转移

**结果转换**
- RPC 结果到 HTTP 响应的自动转换
- 异常处理和错误码映射
- 响应格式标准化

### 1.3 请求处理流程

```
HTTP 请求
    │
    ▼
┌──────────────┐
│  路由匹配    │ ─── 根据 HTTP Path 和 Method 匹配 API 映射规则
└──────────────┘
    │
    ▼
┌──────────────┐
│  参数解析    │ ─── 从 Path/Query/Header/Body 中提取参数
└──────────────┘
    │
    ▼
┌──────────────┐
│  参数转换    │ ─── 根据映射规则进行参数类型转换
└──────────────┘
    │
    ▼
┌──────────────┐
│  协议适配    │ ─── 选择对应的 RPC 协议适配器
└──────────────┘
    │
    ▼
┌──────────────┐
│  泛化调用    │ ─── 执行 RPC 泛化调用
└──────────────┘
    │
    ▼
┌──────────────┐
│  结果转换    │ ─── 将 RPC 结果转换为 HTTP 响应
└──────────────┘
    │
    ▼
HTTP 响应
```

---

## 2. API 映射配置规范（YAML 格式）

### 2.1 基本配置结构

```yaml
api_mappings:
  - http_path: /api/v1/users/{id}
    http_method: GET
    rpc_config:
      protocol: dubbo
      service: com.example.UserService
      version: 1.0.0
      group: default
      method: getUserById
      timeout: 3000
    param_mapping:
      - http_param: id
        rpc_param: userId
        param_type: java.lang.Long
        source: path
      - http_param: X-User-Id
        rpc_param: operatorId
        param_type: java.lang.String
        source: header
    result_mapping:
      success_code: 200
      result_field: data
      wrap_response: true
```

### 2.2 配置字段详解

#### 2.2.1 HTTP 配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `http_path` | String | 是 | HTTP 请求路径，支持路径参数 `{param}` |
| `http_method` | String | 是 | HTTP 方法：GET、POST、PUT、DELETE、PATCH |

#### 2.2.2 RPC 配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `protocol` | String | 是 | RPC 协议：dubbo、grpc、sofa 等 |
| `service` | String | 是 | RPC 服务接口全限定名 |
| `version` | String | 否 | 服务版本号，默认为空 |
| `group` | String | 否 | 服务分组，默认为 default |
| `method` | String | 是 | RPC 方法名 |
| `timeout` | Integer | 否 | 超时时间（毫秒），默认 3000 |

#### 2.2.3 参数映射配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `http_param` | String | 是 | HTTP 参数名称 |
| `rpc_param` | String | 是 | RPC 参数名称 |
| `param_type` | String | 是 | RPC 参数类型全限定名 |
| `source` | String | 是 | 参数来源：path、query、header、body |
| `required` | Boolean | 否 | 是否必填，默认 false |
| `default_value` | String | 否 | 默认值 |

#### 2.2.4 结果映射配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `success_code` | Integer | 否 | 成功响应码，默认 200 |
| `result_field` | String | 否 | 结果字段名，用于提取返回值 |
| `wrap_response` | Boolean | 否 | 是否包装响应体，默认 true |

### 2.3 完整配置示例

#### 2.3.1 简单查询接口

```yaml
api_mappings:
  - http_path: /api/v1/users/{id}
    http_method: GET
    rpc_config:
      protocol: dubbo
      service: com.example.UserService
      version: 1.0.0
      group: default
      method: getUserById
      timeout: 3000
    param_mapping:
      - http_param: id
        rpc_param: userId
        param_type: java.lang.Long
        source: path
        required: true
    result_mapping:
      success_code: 200
      result_field: data
      wrap_response: true
```

#### 2.3.2 复杂对象参数

```yaml
api_mappings:
  - http_path: /api/v1/orders
    http_method: POST
    rpc_config:
      protocol: dubbo
      service: com.example.OrderService
      version: 1.0.0
      method: createOrder
      timeout: 5000
    param_mapping:
      - http_param: body
        rpc_param: orderDTO
        param_type: com.example.dto.OrderDTO
        source: body
        required: true
      - http_param: X-Request-Id
        rpc_param: requestId
        param_type: java.lang.String
        source: header
      - http_param: X-User-Id
        rpc_param: operatorId
        param_type: java.lang.String
        source: header
        required: true
    result_mapping:
      success_code: 201
      result_field: data
      wrap_response: true
```

#### 2.3.3 多参数接口

```yaml
api_mappings:
  - http_path: /api/v1/products
    http_method: GET
    rpc_config:
      protocol: dubbo
      service: com.example.ProductService
      version: 1.0.0
      method: queryProducts
      timeout: 3000
    param_mapping:
      - http_param: category
        rpc_param: categoryId
        param_type: java.lang.Long
        source: query
      - http_param: keyword
        rpc_param: keyword
        param_type: java.lang.String
        source: query
      - http_param: page
        rpc_param: pageNum
        param_type: java.lang.Integer
        source: query
        default_value: "1"
      - http_param: size
        rpc_param: pageSize
        param_type: java.lang.Integer
        source: query
        default_value: "20"
    result_mapping:
      success_code: 200
      result_field: data
      wrap_response: true
```

#### 2.3.4 gRPC 接口配置

```yaml
api_mappings:
  - http_path: /api/v1/grpc/users/{id}
    http_method: GET
    rpc_config:
      protocol: grpc
      service: com.example.UserServiceGrpc
      method: getUser
      timeout: 3000
    param_mapping:
      - http_param: id
        rpc_param: userId
        param_type: long
        source: path
        required: true
    result_mapping:
      success_code: 200
      result_field: user
      wrap_response: true
```

---

## 3. 泛化调用核心实现（Dubbo 示例）

### 3.1 泛化调用原理

泛化调用是 Dubbo 提供的一种无需依赖服务接口 SDK 即可完成 RPC 调用的机制。通过泛化调用，网关可以在不引入服务接口依赖的情况下，动态调用任意 Dubbo 服务。

**核心特点**
- 无需服务接口定义（.jar 依赖）
- 运行时动态构建调用参数
- 支持所有 Dubbo 特性（负载均衡、集群容错等）

### 3.2 核心实现代码

#### 3.2.1 泛化调用器

```java
package com.gateway.rpc.dubbo;

import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DubboGenericInvoker {
    
    private static final Logger logger = LoggerFactory.getLogger(DubboGenericInvoker.class);
    
    private final RegistryConfig registryConfig;
    
    private final Map<String, ReferenceConfig<GenericService>> referenceCache = new ConcurrentHashMap<>();
    
    public DubboGenericInvoker(RegistryConfig registryConfig) {
        this.registryConfig = registryConfig;
    }
    
    public Object invoke(String service, String method, 
                         String[] paramTypes, Object[] params) {
        return invoke(service, method, null, null, paramTypes, params, 3000);
    }
    
    public Object invoke(String service, String method, 
                         String version, String group,
                         String[] paramTypes, Object[] params, 
                         int timeout) {
        String cacheKey = buildCacheKey(service, version, group);
        
        ReferenceConfig<GenericService> reference = referenceCache.computeIfAbsent(
            cacheKey, 
            key -> createReference(service, version, group, timeout)
        );
        
        GenericService genericService = reference.get();
        
        try {
            logger.info("Invoking Dubbo service: {}.{}, paramTypes: {}", 
                       service, method, paramTypes);
            
            Object result = genericService.$invoke(method, paramTypes, params);
            
            logger.info("Dubbo invocation successful: {}.{}", service, method);
            return result;
            
        } catch (Exception e) {
            logger.error("Dubbo invocation failed: {}.{}, error: {}", 
                        service, method, e.getMessage(), e);
            throw new RpcInvocationException("Dubbo invocation failed", e);
        }
    }
    
    private ReferenceConfig<GenericService> createReference(String service, 
                                                            String version,
                                                            String group, 
                                                            int timeout) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        reference.setRegistry(registryConfig);
        reference.setInterface(service);
        reference.setGeneric("true");
        reference.setTimeout(timeout);
        
        if (version != null && !version.isEmpty()) {
            reference.setVersion(version);
        }
        
        if (group != null && !group.isEmpty()) {
            reference.setGroup(group);
        }
        
        return reference;
    }
    
    private String buildCacheKey(String service, String version, String group) {
        StringBuilder sb = new StringBuilder(service);
        if (version != null && !version.isEmpty()) {
            sb.append(":v:").append(version);
        }
        if (group != null && !group.isEmpty()) {
            sb.append(":g:").append(group);
        }
        return sb.toString();
    }
    
    public void destroy() {
        referenceCache.values().forEach(ReferenceConfig::destroy);
        referenceCache.clear();
        logger.info("Dubbo generic invoker destroyed");
    }
}
```

#### 3.2.2 调用上下文

```java
package com.gateway.rpc.dubbo;

import java.util.HashMap;
import java.util.Map;

public class RpcInvocation {
    
    private String protocol;
    private String service;
    private String method;
    private String version;
    private String group;
    private int timeout = 3000;
    
    private String[] paramTypes;
    private Object[] params;
    
    private Map<String, String> attachments = new HashMap<>();
    
    public String getProtocol() {
        return protocol;
    }
    
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
    
    public String getService() {
        return service;
    }
    
    public void setService(String service) {
        this.service = service;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod(String method) {
        this.method = method;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public String[] getParamTypes() {
        return paramTypes;
    }
    
    public void setParamTypes(String[] paramTypes) {
        this.paramTypes = paramTypes;
    }
    
    public Object[] getParams() {
        return params;
    }
    
    public void setParams(Object[] params) {
        this.params = params;
    }
    
    public Map<String, String> getAttachments() {
        return attachments;
    }
    
    public void setAttachments(Map<String, String> attachments) {
        this.attachments = attachments;
    }
    
    public void addAttachment(String key, String value) {
        this.attachments.put(key, value);
    }
}
```

#### 3.2.3 Dubbo 适配器实现

```java
package com.gateway.rpc.adapter;

import com.gateway.rpc.dubbo.DubboGenericInvoker;
import com.gateway.rpc.dubbo.RpcInvocation;
import org.apache.dubbo.config.RegistryConfig;

public class DubboRpcAdapter implements RpcAdapter {
    
    private static final String PROTOCOL = "dubbo";
    
    private DubboGenericInvoker invoker;
    
    private RegistryConfig registryConfig;
    
    @Override
    public String getProtocol() {
        return PROTOCOL;
    }
    
    @Override
    public void initialize(RpcAdapterConfig config) {
        this.registryConfig = new RegistryConfig();
        this.registryConfig.setAddress(config.getRegistryAddress());
        this.registryConfig.setProtocol(config.getRegistryProtocol());
        
        if (config.getUsername() != null) {
            this.registryConfig.setUsername(config.getUsername());
        }
        if (config.getPassword() != null) {
            this.registryConfig.setPassword(config.getPassword());
        }
        
        this.invoker = new DubboGenericInvoker(registryConfig);
    }
    
    @Override
    public Object invoke(RpcInvocation invocation) {
        return invoker.invoke(
            invocation.getService(),
            invocation.getMethod(),
            invocation.getVersion(),
            invocation.getGroup(),
            invocation.getParamTypes(),
            invocation.getParams(),
            invocation.getTimeout()
        );
    }
    
    @Override
    public void destroy() {
        if (invoker != null) {
            invoker.destroy();
        }
    }
}
```

### 3.3 复杂参数处理

#### 3.3.1 POJO 参数构建

```java
package com.gateway.rpc.dubbo;

import java.util.Map;

public class PojoParamBuilder {
    
    public static Object buildPojo(String className, Map<String, Object> fields) {
        try {
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String fieldName = entry.getKey();
                Object fieldValue = entry.getValue();
                
                setFieldValue(instance, fieldName, fieldValue);
            }
            
            return instance;
            
        } catch (Exception e) {
            throw new ParamBuildException("Failed to build POJO: " + className, e);
        }
    }
    
    private static void setFieldValue(Object instance, String fieldName, Object value) 
            throws Exception {
        Class<?> clazz = instance.getClass();
        java.lang.reflect.Field field = findField(clazz, fieldName);
        
        if (field != null) {
            field.setAccessible(true);
            field.set(instance, convertValue(value, field.getType()));
        }
    }
    
    private static java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
    
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        String strValue = value.toString();
        
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(strValue);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(strValue);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(strValue);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(strValue);
        }
        
        return value;
    }
}
```

#### 3.3.2 集合参数处理

```java
package com.gateway.rpc.dubbo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectionParamHandler {
    
    public static Object[] handleListParam(Object param, String elementType) {
        if (param instanceof List) {
            List<?> list = (List<?>) param;
            return list.toArray();
        } else if (param instanceof Object[]) {
            return (Object[]) param;
        } else if (param instanceof String) {
            String[] elements = ((String) param).split(",");
            return Arrays.stream(elements)
                         .map(e -> convertElement(e.trim(), elementType))
                         .toArray();
        }
        return new Object[]{param};
    }
    
    public static Map<String, Object> handleMapParam(Object param) {
        if (param instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) param;
            return map;
        }
        return new HashMap<>();
    }
    
    private static Object convertElement(String value, String elementType) {
        switch (elementType) {
            case "java.lang.Integer":
            case "int":
                return Integer.valueOf(value);
            case "java.lang.Long":
            case "long":
                return Long.valueOf(value);
            case "java.lang.Double":
            case "double":
                return Double.valueOf(value);
            case "java.lang.Boolean":
            case "boolean":
                return Boolean.valueOf(value);
            default:
                return value;
        }
    }
}
```

---

## 4. 参数转换规则

### 4.1 参数来源与转换规则

| HTTP 来源 | 转换规则 | RPC 参数类型 | 示例 |
|-----------|----------|--------------|------|
| Path 参数 | `/users/{id}` -> `id=123` | 基本类型/String | `Long userId = 123L` |
| Query 参数 | `?name=test` -> `name=test` | 基本类型/String/List | `String name = "test"` |
| Header | `X-Token: xxx` -> `token=xxx` | String | `String token = "xxx"` |
| Body(JSON) | JSON -> POJO | 复杂对象/Map | `UserDTO user = {...}` |

### 4.2 类型转换规则

#### 4.2.1 基本类型转换

```java
package com.gateway.converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BasicTypeConverter {
    
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    public static Object convert(Object value, String targetType) {
        if (value == null) {
            return null;
        }
        
        String strValue = value.toString();
        
        switch (targetType) {
            case "java.lang.String":
            case "String":
                return strValue;
                
            case "java.lang.Integer":
            case "int":
                return Integer.valueOf(strValue);
                
            case "java.lang.Long":
            case "long":
                return Long.valueOf(strValue);
                
            case "java.lang.Double":
            case "double":
                return Double.valueOf(strValue);
                
            case "java.lang.Float":
            case "float":
                return Float.valueOf(strValue);
                
            case "java.lang.Boolean":
            case "boolean":
                return parseBoolean(strValue);
                
            case "java.lang.Short":
            case "short":
                return Short.valueOf(strValue);
                
            case "java.lang.Byte":
            case "byte":
                return Byte.valueOf(strValue);
                
            case "java.lang.Character":
            case "char":
                return strValue.charAt(0);
                
            case "java.math.BigDecimal":
                return new BigDecimal(strValue);
                
            case "java.math.BigInteger":
                return new BigInteger(strValue);
                
            case "java.util.Date":
                return parseDate(strValue);
                
            case "java.lang.Object":
                return value;
                
            default:
                return value;
        }
    }
    
    private static Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return Boolean.TRUE;
        } else if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return Boolean.FALSE;
        }
        return Boolean.valueOf(value);
    }
    
    private static Date parseDate(String value) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DEFAULT_DATE_FORMAT);
            return sdf.parse(value);
        } catch (ParseException e) {
            try {
                return new Date(Long.valueOf(value));
            } catch (NumberFormatException nfe) {
                throw new TypeConversionException("Cannot parse date: " + value, e);
            }
        }
    }
}
```

#### 4.2.2 复杂对象转换

```java
package com.gateway.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.util.Map;

public class ComplexTypeConverter {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    public static Object convertToPojo(Object value, String targetType) {
        if (value == null) {
            return null;
        }
        
        try {
            Class<?> targetClass = Class.forName(targetType);
            
            if (targetClass.isInstance(value)) {
                return value;
            }
            
            String json = objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json, targetClass);
            
        } catch (Exception e) {
            throw new TypeConversionException(
                "Failed to convert to POJO: " + targetType, e);
        }
    }
    
    public static Map<String, Object> convertToMap(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return map;
        }
        
        try {
            String json = objectMapper.writeValueAsString(value);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map;
        } catch (Exception e) {
            throw new TypeConversionException("Failed to convert to Map", e);
        }
    }
}
```

### 4.3 参数解析器

```java
package com.gateway.param;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.RequestBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpParamExtractor {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static Map<String, Object> extractParams(RoutingContext ctx, 
                                                     List<ParamMapping> mappings) {
        Map<String, Object> params = new HashMap<>();
        
        for (ParamMapping mapping : mappings) {
            Object value = extractParam(ctx, mapping);
            if (value != null) {
                params.put(mapping.getRpcParam(), value);
            } else if (mapping.isRequired()) {
                throw new RequiredParamMissingException(
                    "Required parameter missing: " + mapping.getHttpParam());
            } else if (mapping.getDefaultValue() != null) {
                params.put(mapping.getRpcParam(), 
                          mapping.getDefaultValue());
            }
        }
        
        return params;
    }
    
    private static Object extractParam(RoutingContext ctx, ParamMapping mapping) {
        String source = mapping.getSource();
        String httpParam = mapping.getHttpParam();
        
        switch (source.toLowerCase()) {
            case "path":
                return ctx.pathParam(httpParam);
                
            case "query":
                return extractQueryParam(ctx, mapping);
                
            case "header":
                return ctx.request().getHeader(httpParam);
                
            case "body":
                return extractBodyParam(ctx, mapping);
                
            default:
                return null;
        }
    }
    
    private static Object extractQueryParam(RoutingContext ctx, ParamMapping mapping) {
        String httpParam = mapping.getHttpParam();
        String paramType = mapping.getParamType();
        
        if (paramType != null && paramType.startsWith("java.util.List")) {
            List<String> values = ctx.queryParams().getAll(httpParam);
            return new ArrayList<>(values);
        }
        
        return ctx.queryParams().get(httpParam);
    }
    
    private static Object extractBodyParam(RoutingContext ctx, ParamMapping mapping) {
        RequestBody body = ctx.body();
        
        if (body == null || body.buffer() == null) {
            return null;
        }
        
        String paramType = mapping.getParamType();
        
        if ("java.lang.String".equals(paramType)) {
            return body.asString();
        }
        
        if (paramType != null && paramType.startsWith("java.util.Map")) {
            return body.asJsonObject().getMap();
        }
        
        return body.asJsonObject().getMap();
    }
}
```

### 4.4 参数校验

```java
package com.gateway.validator;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ParamValidator {
    
    public static void validate(Object value, ParamMapping mapping) {
        if (value == null) {
            if (mapping.isRequired()) {
                throw new ValidationException(
                    "Parameter '" + mapping.getHttpParam() + "' is required");
            }
            return;
        }
        
        String paramType = mapping.getParamType();
        
        validateType(value, paramType);
        
        if (mapping.getValidation() != null) {
            ValidationRule rule = mapping.getValidation();
            
            if (rule.getPattern() != null) {
                validatePattern(value.toString(), rule.getPattern(), mapping.getHttpParam());
            }
            
            if (rule.getMin() != null || rule.getMax() != null) {
                validateRange(value, rule, mapping.getHttpParam());
            }
            
            if (rule.getEnumValues() != null && !rule.getEnumValues().isEmpty()) {
                validateEnum(value, rule.getEnumValues(), mapping.getHttpParam());
            }
        }
    }
    
    private static void validateType(Object value, String paramType) {
        switch (paramType) {
            case "java.lang.Integer":
            case "int":
                if (!(value instanceof Integer)) {
                    throw new ValidationException("Expected Integer type");
                }
                break;
            case "java.lang.Long":
            case "long":
                if (!(value instanceof Long)) {
                    throw new ValidationException("Expected Long type");
                }
                break;
            case "java.lang.String":
                if (!(value instanceof String)) {
                    throw new ValidationException("Expected String type");
                }
                break;
        }
    }
    
    private static void validatePattern(String value, String pattern, String paramName) {
        if (!Pattern.matches(pattern, value)) {
            throw new ValidationException(
                "Parameter '" + paramName + "' does not match pattern: " + pattern);
        }
    }
    
    private static void validateRange(Object value, ValidationRule rule, String paramName) {
        if (value instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            
            if (rule.getMin() != null && numValue < rule.getMin()) {
                throw new ValidationException(
                    "Parameter '" + paramName + "' is less than minimum: " + rule.getMin());
            }
            
            if (rule.getMax() != null && numValue > rule.getMax()) {
                throw new ValidationException(
                    "Parameter '" + paramName + "' is greater than maximum: " + rule.getMax());
            }
        }
    }
    
    private static void validateEnum(Object value, List<Object> enumValues, String paramName) {
        if (!enumValues.contains(value)) {
            throw new ValidationException(
                "Parameter '" + paramName + "' must be one of: " + enumValues);
        }
    }
}
```

---

## 5. 服务发现集成方案

### 5.1 服务发现架构

```
┌─────────────────────────────────────────────────────────────┐
│                      服务发现集成层                          │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   服务注册中心                        │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │   │
│  │  │  Nacos  │ │Zookeeper│ │ Consul  │ │  Eureka │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   服务实例缓存                        │   │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐       │   │
│  │  │ 本地缓存  │  │ 订阅监听  │  │ 健康检查  │       │   │
│  │  └───────────┘  └───────────┘  └───────────┘       │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   负载均衡策略                        │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │   │
│  │  │ Random  │ │RoundRobin│ │Weighted │ │ Hash   │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 服务发现接口定义

```java
package com.gateway.discovery;

import java.util.List;

public interface ServiceDiscovery {
    
    void initialize(DiscoveryConfig config);
    
    void register(ServiceInstance instance);
    
    void deregister(ServiceInstance instance);
    
    List<ServiceInstance> getInstances(String serviceName);
    
    void subscribe(String serviceName, ServiceChangeListener listener);
    
    void unsubscribe(String serviceName, ServiceChangeListener listener);
    
    void destroy();
}
```

### 5.3 Nacos 服务发现实现

```java
package com.gateway.discovery.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.gateway.discovery.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class NacosServiceDiscovery implements ServiceDiscovery {
    
    private NamingService namingService;
    
    private final Map<String, EventListener> listenerMap = new ConcurrentHashMap<>();
    
    @Override
    public void initialize(DiscoveryConfig config) {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", config.getServerAddr());
            properties.setProperty("namespace", config.getNamespace());
            
            if (config.getUsername() != null) {
                properties.setProperty("username", config.getUsername());
            }
            if (config.getPassword() != null) {
                properties.setProperty("password", config.getPassword());
            }
            
            this.namingService = NacosFactory.createNamingService(properties);
            
        } catch (Exception e) {
            throw new DiscoveryException("Failed to initialize Nacos naming service", e);
        }
    }
    
    @Override
    public void register(ServiceInstance instance) {
        try {
            namingService.registerInstance(
                instance.getServiceName(),
                instance.getIp(),
                instance.getPort(),
                instance.getClusterName()
            );
        } catch (Exception e) {
            throw new DiscoveryException("Failed to register service instance", e);
        }
    }
    
    @Override
    public void deregister(ServiceInstance instance) {
        try {
            namingService.deregisterInstance(
                instance.getServiceName(),
                instance.getIp(),
                instance.getPort(),
                instance.getClusterName()
            );
        } catch (Exception e) {
            throw new DiscoveryException("Failed to deregister service instance", e);
        }
    }
    
    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        try {
            List<Instance> instances = namingService.selectInstances(serviceName, true);
            return convertInstances(instances, serviceName);
        } catch (Exception e) {
            throw new DiscoveryException("Failed to get service instances", e);
        }
    }
    
    @Override
    public void subscribe(String serviceName, ServiceChangeListener listener) {
        try {
            EventListener eventListener = event -> {
                if (event instanceof NamingEvent) {
                    NamingEvent namingEvent = (NamingEvent) event;
                    List<ServiceInstance> instances = convertInstances(
                        namingEvent.getInstances(), serviceName);
                    listener.onChanged(instances);
                }
            };
            
            namingService.subscribe(serviceName, eventListener);
            listenerMap.put(serviceName, eventListener);
            
        } catch (Exception e) {
            throw new DiscoveryException("Failed to subscribe service: " + serviceName, e);
        }
    }
    
    @Override
    public void unsubscribe(String serviceName, ServiceChangeListener listener) {
        try {
            EventListener eventListener = listenerMap.remove(serviceName);
            if (eventListener != null) {
                namingService.unsubscribe(serviceName, eventListener);
            }
        } catch (Exception e) {
            throw new DiscoveryException("Failed to unsubscribe service: " + serviceName, e);
        }
    }
    
    @Override
    public void destroy() {
        try {
            listenerMap.keySet().forEach(serviceName -> {
                try {
                    namingService.unsubscribe(serviceName, listenerMap.get(serviceName));
                } catch (Exception ignored) {
                }
            });
            listenerMap.clear();
        } catch (Exception e) {
            throw new DiscoveryException("Failed to destroy service discovery", e);
        }
    }
    
    private List<ServiceInstance> convertInstances(List<Instance> instances, String serviceName) {
        List<ServiceInstance> result = new ArrayList<>();
        for (Instance instance : instances) {
            ServiceInstance si = new ServiceInstance();
            si.setServiceName(serviceName);
            si.setIp(instance.getIp());
            si.setPort(instance.getPort());
            si.setWeight(instance.getWeight());
            si.setHealthy(instance.isHealthy());
            si.setMetadata(instance.getMetadata());
            si.setClusterName(instance.getClusterName());
            result.add(si);
        }
        return result;
    }
}
```

### 5.4 Zookeeper 服务发现实现

```java
package com.gateway.discovery.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import com.gateway.discovery.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ZookeeperServiceDiscovery implements ServiceDiscovery {
    
    private static final String NAMESPACE = "/services";
    
    private CuratorFramework client;
    
    private final Map<String, PathChildrenCache> cacheMap = new ConcurrentHashMap<>();
    
    @Override
    public void initialize(DiscoveryConfig config) {
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
            .connectString(config.getServerAddr())
            .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            .connectionTimeoutMs(config.getConnectTimeout() > 0 ? config.getConnectTimeout() : 5000)
            .sessionTimeoutMs(config.getSessionTimeout() > 0 ? config.getSessionTimeout() : 30000);
        
        this.client = builder.build();
        this.client.start();
    }
    
    @Override
    public void register(ServiceInstance instance) {
        try {
            String path = buildPath(instance.getServiceName(), instance.getId());
            String data = serializeInstance(instance);
            
            if (client.checkExists().forPath(path) != null) {
                client.setData().forPath(path, data.getBytes(StandardCharsets.UTF_8));
            } else {
                client.create().creatingParentsIfNeeded().forPath(path, data.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new DiscoveryException("Failed to register service instance", e);
        }
    }
    
    @Override
    public void deregister(ServiceInstance instance) {
        try {
            String path = buildPath(instance.getServiceName(), instance.getId());
            client.delete().quietly().forPath(path);
        } catch (Exception e) {
            throw new DiscoveryException("Failed to deregister service instance", e);
        }
    }
    
    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        try {
            String servicePath = NAMESPACE + "/" + serviceName;
            
            if (client.checkExists().forPath(servicePath) == null) {
                return new ArrayList<>();
            }
            
            List<String> children = client.getChildren().forPath(servicePath);
            List<ServiceInstance> instances = new ArrayList<>();
            
            for (String child : children) {
                String path = servicePath + "/" + child;
                byte[] data = client.getData().forPath(path);
                instances.add(deserializeInstance(data, serviceName));
            }
            
            return instances;
        } catch (Exception e) {
            throw new DiscoveryException("Failed to get service instances", e);
        }
    }
    
    @Override
    public void subscribe(String serviceName, ServiceChangeListener listener) {
        try {
            String servicePath = NAMESPACE + "/" + serviceName;
            
            PathChildrenCache cache = new PathChildrenCache(client, servicePath, true);
            cache.getListenable().addListener((client1, event) -> {
                if (event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED ||
                    event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED ||
                    event.getType() == PathChildrenCacheEvent.Type.CHILD_REMOVED) {
                    
                    List<ServiceInstance> instances = getInstances(serviceName);
                    listener.onChanged(instances);
                }
            });
            
            cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
            cacheMap.put(serviceName, cache);
            
        } catch (Exception e) {
            throw new DiscoveryException("Failed to subscribe service: " + serviceName, e);
        }
    }
    
    @Override
    public void unsubscribe(String serviceName, ServiceChangeListener listener) {
        PathChildrenCache cache = cacheMap.remove(serviceName);
        if (cache != null) {
            try {
                cache.close();
            } catch (Exception ignored) {
            }
        }
    }
    
    @Override
    public void destroy() {
        cacheMap.values().forEach(cache -> {
            try {
                cache.close();
            } catch (Exception ignored) {
            }
        });
        cacheMap.clear();
        
        if (client != null) {
            client.close();
        }
    }
    
    private String buildPath(String serviceName, String instanceId) {
        return NAMESPACE + "/" + serviceName + "/" + instanceId;
    }
    
    private String serializeInstance(ServiceInstance instance) {
        return instance.getIp() + ":" + instance.getPort() + ":" + instance.getWeight();
    }
    
    private ServiceInstance deserializeInstance(byte[] data, String serviceName) {
        String str = new String(data, StandardCharsets.UTF_8);
        String[] parts = str.split(":");
        
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceName(serviceName);
        instance.setIp(parts[0]);
        instance.setPort(Integer.parseInt(parts[1]));
        if (parts.length > 2) {
            instance.setWeight(Double.parseDouble(parts[2]));
        }
        
        return instance;
    }
}
```

### 5.5 负载均衡实现

```java
package com.gateway.loadbalance;

import com.gateway.discovery.ServiceInstance;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public interface LoadBalancer {
    
    ServiceInstance select(List<ServiceInstance> instances, String key);
    
    String getName();
}

public class RandomLoadBalancer implements LoadBalancer {
    
    @Override
    public ServiceInstance select(List<ServiceInstance> instances, String key) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        
        int index = (int) (Math.random() * instances.size());
        return instances.get(index);
    }
    
    @Override
    public String getName() {
        return "random";
    }
}

public class RoundRobinLoadBalancer implements LoadBalancer {
    
    private final ConcurrentMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    
    @Override
    public ServiceInstance select(List<ServiceInstance> instances, String key) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement()) % instances.size();
        
        return instances.get(index);
    }
    
    @Override
    public String getName() {
        return "roundRobin";
    }
}

public class WeightedRandomLoadBalancer implements LoadBalancer {
    
    @Override
    public ServiceInstance select(List<ServiceInstance> instances, String key) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        
        double totalWeight = instances.stream()
            .mapToDouble(ServiceInstance::getWeight)
            .sum();
        
        double random = Math.random() * totalWeight;
        
        double currentWeight = 0;
        for (ServiceInstance instance : instances) {
            currentWeight += instance.getWeight();
            if (random < currentWeight) {
                return instance;
            }
        }
        
        return instances.get(instances.size() - 1);
    }
    
    @Override
    public String getName() {
        return "weightedRandom";
    }
}

public class ConsistentHashLoadBalancer implements LoadBalancer {
    
    private static final int VIRTUAL_NODES = 160;
    
    private final TreeMap<Long, ServiceInstance> hashRing = new TreeMap<>();
    
    @Override
    public ServiceInstance select(List<ServiceInstance> instances, String key) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        
        hashRing.clear();
        
        for (ServiceInstance instance : instances) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                String virtualNode = instance.getIp() + ":" + instance.getPort() + "#" + i;
                long hash = hash(virtualNode);
                hashRing.put(hash, instance);
            }
        }
        
        long hash = hash(key);
        Map.Entry<Long, ServiceInstance> entry = hashRing.ceilingEntry(hash);
        
        if (entry == null) {
            entry = hashRing.firstEntry();
        }
        
        return entry.getValue();
    }
    
    private long hash(String key) {
        return key.hashCode() & 0xffffffffL;
    }
    
    @Override
    public String getName() {
        return "consistentHash";
    }
}
```

### 5.6 服务发现配置

```yaml
discovery:
  type: nacos
  config:
    server_addr: ${NACOS_SERVER_ADDR:localhost:8848}
    namespace: ${NACOS_NAMESPACE:public}
    username: ${NACOS_USERNAME:}
    password: ${NACOS_PASSWORD:}
    group: DEFAULT_GROUP
    
  load_balance:
    default: roundRobin
    strategies:
      - name: random
        class: com.gateway.loadbalance.RandomLoadBalancer
      - name: roundRobin
        class: com.gateway.loadbalance.RoundRobinLoadBalancer
      - name: weightedRandom
        class: com.gateway.loadbalance.WeightedRandomLoadBalancer
      - name: consistentHash
        class: com.gateway.loadbalance.ConsistentHashLoadBalancer
        
  cache:
    enabled: true
    expire_seconds: 30
    max_size: 10000
    
  health_check:
    enabled: true
    interval_seconds: 10
    timeout_seconds: 5
```

---

## 附录

### A. 异常处理

```java
package com.gateway.exception;

public class GatewayException extends RuntimeException {
    
    private final int code;
    
    public GatewayException(int code, String message) {
        super(message);
        this.code = code;
    }
    
    public GatewayException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public int getCode() {
        return code;
    }
}

public class RpcInvocationException extends GatewayException {
    
    public RpcInvocationException(String message) {
        super(500, message);
    }
    
    public RpcInvocationException(String message, Throwable cause) {
        super(500, message, cause);
    }
}

public class TypeConversionException extends GatewayException {
    
    public TypeConversionException(String message) {
        super(400, message);
    }
    
    public TypeConversionException(String message, Throwable cause) {
        super(400, message, cause);
    }
}

public class ValidationException extends GatewayException {
    
    public ValidationException(String message) {
        super(400, message);
    }
}

public class RequiredParamMissingException extends GatewayException {
    
    public RequiredParamMissingException(String message) {
        super(400, message);
    }
}

public class DiscoveryException extends GatewayException {
    
    public DiscoveryException(String message) {
        super(503, message);
    }
    
    public DiscoveryException(String message, Throwable cause) {
        super(503, message, cause);
    }
}
```

### B. 错误码定义

| 错误码 | 说明 | HTTP 状态码 |
|--------|------|-------------|
| 400 | 参数校验失败 | 400 |
| 401 | 认证失败 | 401 |
| 403 | 权限不足 | 403 |
| 404 | 服务或接口不存在 | 404 |
| 500 | RPC 调用失败 | 500 |
| 503 | 服务发现失败 | 503 |
| 504 | 调用超时 | 504 |

### C. 参考资源

- [Apache Dubbo 官方文档](https://dubbo.apache.org/zh/)
- [Nacos 官方文档](https://nacos.io/zh-cn/docs/what-is-nacos.html)
- [gRPC 官方文档](https://grpc.io/docs/)
- [Vert.x 官方文档](https://vertx.io/docs/)
