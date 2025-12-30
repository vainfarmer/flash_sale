# ⚡ 高并发秒杀系统

一个基于 Spring Boot 3 + Redis + Kafka + MySQL 的高性能秒杀系统，能够应对高并发、低库存、短时间爆发式访问的场景。

## 🌿 分支说明

| 分支 | 说明 | 特性 |
|------|------|------|
| **master** | 基础版本 | 完整的秒杀功能（Redis + Lua + Kafka） |
| **order-timeout** | 订单超时 | 新增 Redisson 延迟队列实现订单超时自动取消 |
| **virtual** | 虚拟线程 | 新增 Java 21 虚拟线程支持 + 连接池优化 |

### 分支差异对比

```
master
  └── order-timeout（基于 master）
        ├── + Redisson 延迟队列
        ├── + 订单超时自动取消（30分钟）
        ├── + 订单超时管理接口
        └── + 定时任务监控
              └── virtual（基于 order-timeout）
                    ├── + Java 21 虚拟线程
                    ├── + Tomcat 虚拟线程配置
                    ├── + 优化连接池参数
                    └── + 异步秒杀接口
```

## 📐 系统架构

```
┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
│   用户   │────▶│   网关   │────▶│  Redis  │────▶│   MQ    │────▶│   DB    │
└─────────┘     └─────────┘     └─────────┘     └─────────┘     └─────────┘
                    │                │                │                │
                    │ IP限流         │ Lua脚本        │ 异步下单       │ 乐观锁
                    │ 身份验证       │ 扣减库存       │ 削峰填谷       │ 持久化
```

## 🎯 核心特性

### 1️⃣ 前端层
- ✅ 活动未开始时按钮置灰
- ✅ 倒计时显示
- ✅ 防重复点击

### 2️⃣ 网关层（Gateway）
- ✅ **IP限流**：每秒最多10次请求，防止恶意攻击
- ✅ **黑名单拦截**：封禁恶意IP
- ✅ **JWT身份验证**：Token验证用户身份

### 3️⃣ 缓存层（Redis）
- ✅ **Lua脚本原子操作**：库存扣减 + 重复购买检查 + 限购检查
- ✅ **数据预热**：应用启动时自动预热商品数据
- ✅ **高性能**：QPS可达10万+

### 4️⃣ 消息队列（Kafka）
- ✅ **削峰填谷**：秒杀请求异步化处理
- ✅ **订单消息**：保证消息可靠投递
- ✅ **手动确认**：消费成功后才提交offset

### 5️⃣ 数据库层（MySQL）
- ✅ **乐观锁**：版本号控制，保证库存一致性
- ✅ **幂等性**：订单号唯一，防止重复创建

## 📁 项目结构

```
flash_sale/
├── src/main/java/org/example/flash_sale/
│   ├── FlashSaleApplication.java       # 启动类
│   ├── common/                          # 通用组件
│   │   ├── Constants.java              # 常量定义
│   │   └── Result.java                 # 统一响应
│   ├── config/                          # 配置类
│   │   ├── FilterConfig.java           # 过滤器配置
│   │   ├── JwtConfig.java              # JWT配置
│   │   ├── KafkaConfig.java            # Kafka配置
│   │   ├── KafkaConsumerConfig.java    # Kafka消费者配置
│   │   ├── MyBatisPlusConfig.java      # MyBatis Plus配置
│   │   ├── RedisConfig.java            # Redis配置
│   │   └── WebConfig.java              # Web配置
│   ├── controller/                      # 控制器
│   │   ├── FlashSaleController.java    # 秒杀接口
│   │   ├── OrderController.java        # 订单接口
│   │   └── UserController.java         # 用户接口
│   ├── dto/                             # 数据传输对象
│   │   ├── FlashSaleRequest.java
│   │   ├── FlashSaleResponse.java
│   │   └── OrderMessage.java
│   ├── entity/                          # 实体类
│   │   ├── Order.java
│   │   ├── Product.java
│   │   └── User.java
│   ├── exception/                       # 异常处理
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
│   ├── filter/                          # 过滤器
│   │   ├── AuthFilter.java             # 身份验证
│   │   └── RateLimitFilter.java        # 限流
│   ├── listener/                        # 监听器
│   │   └── CacheWarmUpListener.java    # 缓存预热
│   ├── mapper/                          # 数据访问层
│   │   ├── OrderMapper.java
│   │   ├── ProductMapper.java
│   │   └── UserMapper.java
│   ├── mq/                              # 消息队列
│   │   ├── OrderConsumer.java          # 订单消费者
│   │   └── OrderProducer.java          # 订单生产者
│   └── service/                         # 服务层
│       ├── FlashSaleService.java
│       ├── OrderService.java
│       ├── ProductService.java
│       └── impl/
│           ├── FlashSaleServiceImpl.java
│           ├── OrderServiceImpl.java
│           └── ProductServiceImpl.java
├── src/main/resources/
│   ├── application.yml                  # 配置文件
│   ├── schema.sql                       # 数据库脚本
│   ├── lua/                             # Lua脚本
│   │   ├── stock_deduct.lua            # 库存扣减
│   │   └── stock_rollback.lua          # 库存回滚
│   └── static/
│       └── index.html                   # 前端页面
└── pom.xml
```

## 🔧 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.0 | 基础框架 |
| Redis | - | 缓存、分布式锁 |
| Kafka | - | 消息队列 |
| MySQL | 8.0+ | 数据库 |
| MyBatis Plus | 3.5.5 | ORM框架 |
| JWT | 0.12.3 | Token认证 |
| Hutool | 5.8.23 | 工具类 |

## 🚀 快速开始

### 1. 环境准备

确保已安装以下服务：
- MySQL 8.0+
- Redis 6.0+
- Kafka 3.0+

### 2. 初始化数据库

```bash
mysql -u root -p < src/main/resources/schema.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`，修改数据库、Redis、Kafka连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/flash_sale
    username: your_username
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092
```

### 4. 启动应用

```bash
./mvnw spring-boot:run
```

### 5. 访问页面

打开浏览器访问：http://localhost:8080

测试账号：
- 用户名：`user1`，密码：`123456`
- 用户名：`user2`，密码：`123456`

## 📡 API接口

### 用户接口
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/user/login | 用户登录 |
| GET | /api/user/info | 获取用户信息 |

### 秒杀接口
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/flash/products | 获取商品列表 |
| GET | /api/flash/product/{id} | 获取商品详情 |
| GET | /api/flash/check/{productId} | 检查活动状态 |
| POST | /api/flash/do | 执行秒杀 |
| POST | /api/flash/admin/warmup | 预热所有缓存 |

### 订单接口
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/flash/order/{orderNo} | 查询订单 |
| GET | /api/flash/order/list | 查询订单列表 |
| POST | /api/flash/order/cancel/{orderNo} | 取消订单 |
| POST | /api/flash/order/pay/{orderNo} | 支付订单 |

## 🔐 秒杀流程

```
1. 用户发起秒杀请求
       ↓
2. RateLimitFilter：IP限流检查
       ↓
3. AuthFilter：JWT身份验证
       ↓
4. FlashSaleService：检查活动状态
       ↓
5. Redis Lua脚本（原子操作）：
   - 检查用户是否已购买
   - 检查是否超出限购
   - 检查库存是否充足
   - 扣减库存
   - 记录用户购买
       ↓
6. 生成订单号
       ↓
7. 发送订单消息到Kafka
       ↓
8. 返回秒杀结果给用户
       ↓
9. Kafka消费者异步处理：
   - 创建订单记录
   - 扣减数据库库存
```

## ⚡ Lua脚本说明

### stock_deduct.lua（库存扣减）

```lua
-- 原子性操作：检查库存 + 检查重复购买 + 扣减库存 + 记录购买
-- 返回值：
--   -1: 库存不足
--   -2: 重复购买/超出限购
--  >=0: 扣减成功，返回剩余库存
```

## 📊 性能优化建议

1. **Redis集群**：使用Redis Cluster提升缓存性能
2. **Kafka分区**：根据商品ID分区，提升并行消费能力
3. **数据库读写分离**：主库写，从库读
4. **连接池调优**：根据实际并发量调整连接池大小
5. **JVM调优**：调整堆内存、GC策略

## 📝 注意事项

1. 生产环境需要修改JWT密钥
2. 建议使用Nginx做负载均衡
3. 建议开启Redis持久化
4. 建议Kafka设置多副本

---

## 🌿 分支详细说明

### 📌 master 分支

基础版本，包含完整的秒杀核心功能：

- Redis + Lua 脚本原子扣减库存
- Kafka 异步订单处理
- IP 限流 + JWT 身份验证
- 乐观锁保证数据一致性

### 📌 order-timeout 分支

在 master 基础上新增**订单超时自动取消**功能：

#### 新增功能

| 功能 | 说明 |
|------|------|
| Redisson 延迟队列 | 订单创建后自动加入超时队列 |
| 超时自动取消 | 30分钟未支付自动取消订单 |
| 库存自动回滚 | 取消时回滚 Redis + MySQL 库存 |
| 管理接口 | 查看/操作超时队列 |

#### 配置项

```yaml
order:
  timeout:
    minutes: 30      # 超时时间（分钟）
    enabled: true    # 是否启用
```

#### 新增文件

```
+ service/OrderTimeoutService.java
+ service/impl/OrderTimeoutServiceImpl.java
+ controller/OrderTimeoutController.java
```

#### 管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/admin/order-timeout/status` | GET | 查看队列状态 |
| `/api/admin/order-timeout/add/{orderNo}` | POST | 手动加入队列 |
| `/api/admin/order-timeout/remove/{orderNo}` | POST | 从队列移除 |
| `/api/admin/order-timeout/process/{orderNo}` | POST | 手动处理超时 |

### 📌 virtual 分支

在 order-timeout 基础上新增**虚拟线程优化**（需要 Java 21+）：

#### 新增功能

| 功能 | 说明 |
|------|------|
| Tomcat 虚拟线程 | 请求处理使用虚拟线程 |
| 异步秒杀接口 | 使用虚拟线程执行器 |
| 连接池优化 | MySQL 100连接，Redis 500连接 |

#### 配置变更

```yaml
spring:
  threads:
    virtual:
      enabled: true    # 启用虚拟线程

  datasource:
    hikari:
      maximum-pool-size: 100   # 提高数据库连接数

  data:
    redis:
      lettuce:
        pool:
          max-active: 500      # 提高Redis连接数
```

#### 新增文件

```
+ config/AsyncConfig.java      # 异步配置（虚拟线程执行器）
```

#### 新增接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/flash/test/do-async` | POST | 异步秒杀（Redis方案） |
| `/api/flash/test/do-direct-async` | POST | 异步秒杀（直接DB方案） |

#### ⚠️ 虚拟线程注意事项

```
┌─────────────────────────────────────────────────────────────────────┐
│                  虚拟线程适用场景                                     │
├─────────────────────────────────────────────────────────────────────┤
│   ✅ 适合使用虚拟线程：                                               │
│      • Redis 操作（Lettuce 非阻塞）                                  │
│      • Kafka 操作（异步发送）                                        │
│      • HTTP 调用（网络IO等待）                                       │
│                                                                     │
│   ❌ 不适合使用虚拟线程：                                             │
│      • 数据库操作（HikariCP/JDBC 有 synchronized）                   │
│      • 会导致虚拟线程"钉住"(pinning)，造成线程饥饿                    │
└─────────────────────────────────────────────────────────────────────┘
```

**解决方案**：数据库操作使用普通线程池 `taskExecutor`，而非虚拟线程。

---

## 🧪 压力测试

### 测试脚本

```bash
cd scripts

# 安装依赖
pip install -r requirements.txt

# 初始化测试数据
mysql -u root -p flash_sale < init_users.sql
```

### 测试命令

```bash
# Redis + Lua + Kafka 方案（默认）
python flash_sale_test.py --users 500 --concurrency 100 --mode redis

# 直接数据库方案
python flash_sale_test.py --users 500 --concurrency 100 --mode direct

# 异步 Redis 方案（virtual分支）
python flash_sale_test.py --users 500 --concurrency 200 --mode redis-async

# 异步直接数据库方案（virtual分支）
python flash_sale_test.py --users 500 --concurrency 200 --mode direct-async
```

### 重置测试数据

```sql
-- 重置商品库存
UPDATE t_product SET available_stock = 100, version = 0 WHERE id = 1;

-- 清除订单
DELETE FROM t_order WHERE product_id = 1;
```

```bash
# 清除Redis数据
redis-cli DEL flash:bought:1
redis-cli SET flash:stock:1 100
```

## 📜 License

MIT License

