# 秒杀压测脚本

## 📁 文件说明

| 文件 | 说明 |
|------|------|
| `insert_users.sql` | 批量插入500个测试用户的SQL |
| `generate_users.py` | 生成批量用户SQL的工具 |
| `flash_sale_test.py` | 并发压测脚本 |
| `requirements.txt` | Python依赖 |

## 🚀 使用步骤

### 1. 初始化测试数据

```bash
# 进入脚本目录
cd scripts

# 执行SQL插入500个用户，并设置商品库存为100
mysql -u root -p flash_sale < insert_users.sql
```

### 2. 安装Python依赖

```bash
pip install -r requirements.txt
```

### 3. 确保服务已启动

```bash
# 确保Spring Boot应用已启动
# 确保Redis已启动
# 确保MySQL已启动
# Kafka可选（不影响压测）
```

### 4. 运行压测

```bash
# 基本用法：200用户，50并发，抢购商品1
python flash_sale_test.py --users 200 --concurrency 50 --product 1

# 大压力测试：500用户，100并发
python flash_sale_test.py --users 500 --concurrency 100 --product 1

# 指定服务器地址
python flash_sale_test.py --url http://192.168.1.100:8080 --users 300 --concurrency 80 --product 1
```

## 📊 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--url` | http://localhost:8080 | 服务器地址 |
| `--users` | 200 | 参与抢购的用户数 |
| `--concurrency` | 50 | 并发数 |
| `--product` | 1 | 商品ID |

## 📈 测试结果示例

```
============================================================
📊 压测结果统计
============================================================

📈 总体统计:
   总请求数: 200
   成功数量: 100 (50.0%)
   失败数量: 100 (50.0%)
   总耗时: 2.35秒
   QPS: 85.11

⏱️ 响应时间(ms):
   平均: 45.23
   最小: 12.34
   最大: 234.56
   P50: 38.90
   P95: 89.45
   P99: 156.78

❌ 失败原因分布:
   库存不足，秒杀失败: 95次
   您已参与过此活动或超出限购数量: 5次

✅ 成功订单数: 100
============================================================
```

## 🔧 自定义用户数量

如果需要更多用户：

```bash
# 生成1000个用户
python generate_users.py --count 1000 --output insert_1000_users.sql

# 执行SQL
mysql -u root -p flash_sale < insert_1000_users.sql
```

## ⚠️ 注意事项

1. **库存设置**: SQL会将商品1的库存设为100，用户数应大于100才能测试超卖
2. **重复测试**: 每次测试前需要重置库存和清除用户购买记录
3. **Redis清理**: 可能需要清除Redis中的购买记录

### 重置测试数据

```sql
-- 重置商品库存
UPDATE t_product SET available_stock = 100 WHERE id = 1;

-- 清除订单（可选）
DELETE FROM t_order WHERE product_id = 1;
```

```bash
# 清除Redis购买记录
redis-cli DEL flash:bought:1
redis-cli SET flash:stock:1 100
```

---

## ⏰ 订单超时自动取消

### 功能说明

使用 Redisson 延迟队列实现订单超时自动取消：
- 订单创建后自动加入延迟队列
- 超时未支付的订单自动取消
- 取消订单时自动回滚 Redis 和 数据库 库存
- 支付成功后自动从队列移除

### 配置项（application.yml）

```yaml
order:
  timeout:
    minutes: 30      # 超时时间（分钟）
    enabled: true    # 是否启用
```

### 管理接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/admin/order-timeout/status` | GET | 查看队列状态 |
| `/api/admin/order-timeout/add/{orderNo}` | POST | 手动加入队列 |
| `/api/admin/order-timeout/remove/{orderNo}` | POST | 从队列移除 |
| `/api/admin/order-timeout/process/{orderNo}` | POST | 手动处理超时 |
| `/api/admin/order-timeout/process-all-pending` | POST | 批量处理所有待支付 |

---

## 🚀 性能优化（虚拟线程 + 异步）

### 优化内容

1. **虚拟线程（Java 21+）**
   - Tomcat 启用虚拟线程处理请求
   - 异步接口使用虚拟线程执行器

2. **连接池优化**
   - MySQL HikariCP: 最大连接数 100
   - Redis Lettuce: 最大连接数 500

3. **异步秒杀接口**
   - `/api/flash/test/do-async` - Redis方案（异步）
   - `/api/flash/test/do-direct-async` - 直接DB方案（异步）

### 压测模式

| 模式 | 参数 | 说明 |
|------|------|------|
| `redis` | `--mode redis` | Redis+Lua+Kafka（同步） |
| `direct` | `--mode direct` | 直接数据库（同步） |
| `redis-async` | `--mode redis-async` | Redis方案（异步/虚拟线程） |
| `direct-async` | `--mode direct-async` | 直接DB（异步/虚拟线程） |

### 压测命令

```bash
# 同步 Redis 方案
python flash_sale_test.py --users 500 --concurrency 100 --mode redis

# 异步 Redis 方案（虚拟线程）- 可支持更高并发
python flash_sale_test.py --users 500 --concurrency 200 --mode redis-async

# 同步 直接DB 方案
python flash_sale_test.py --users 500 --concurrency 100 --mode direct

# 异步 直接DB 方案（虚拟线程）
python flash_sale_test.py --users 500 --concurrency 200 --mode direct-async
```

### 预期效果

| 模式 | 建议并发 | QPS提升 |
|------|---------|---------|
| 同步 Redis | 50-100 | 基准 |
| 异步 Redis（虚拟线程） | 100-300 | 50-100% |
| 同步 直接DB | 50-100 | 基准 |
| 异步 直接DB（虚拟线程） | 100-200 | 30-50% |

