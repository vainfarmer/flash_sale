# 秒杀压测脚本

## 📁 文件说明

| 文件 | 说明 |
|------|------|
| `insert_users.sql` | 批量插入500个测试用户的SQL |
| `generate_users.py` | 生成批量用户SQL的工具 |
| `flash_sale_test.py` | 并发压测脚本 |
| `test_consistency.py` | 库存一致性检查脚本 |
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

## 🔍 库存一致性检查

### 功能说明

用于检查和修复Redis与数据库之间的库存一致性，确保最终一致性。

### 使用方法

```bash
# 查看一致性报告
python test_consistency.py --action report

# 检查单个商品
python test_consistency.py --action check --product-id 1

# 检查所有商品
python test_consistency.py --action check-all

# 修复单个商品（以数据库为准）
python test_consistency.py --action repair --product-id 1

# 修复单个商品（以Redis为准）
python test_consistency.py --action repair --product-id 1 --use-redis

# 修复所有不一致的商品
python test_consistency.py --action repair-all
```

### API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/admin/consistency/check/{productId}` | GET | 检查单个商品一致性 |
| `/api/admin/consistency/check-all` | GET | 检查所有商品一致性 |
| `/api/admin/consistency/repair/{productId}` | POST | 修复单个商品 |
| `/api/admin/consistency/repair-all` | POST | 修复所有商品 |
| `/api/admin/consistency/report` | GET | 获取详细报告 |

### 定时任务

系统会每5分钟自动检查一致性（可在 `application.yml` 中配置）：

```yaml
flash-sale:
  consistency:
    check-cron: "0 */5 * * * ?"  # 检查间隔
    auto-repair: false           # 是否自动修复
```

