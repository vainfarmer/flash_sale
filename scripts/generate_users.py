#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成批量插入用户的SQL脚本
"""

def generate_users_sql(count: int = 500, output_file: str = "insert_users.sql"):
    """生成批量插入用户的SQL"""
    
    sql_lines = [
        "-- 批量插入测试用户",
        "USE flash_sale;",
        "",
        "-- 清空现有测试用户（保留admin）",
        "DELETE FROM t_user WHERE username LIKE 'user%';",
        "",
        "-- 批量插入用户",
        "INSERT INTO t_user (username, password, phone, status, deleted) VALUES"
    ]
    
    values = []
    for i in range(1, count + 1):
        phone = f"138{i:08d}"
        values.append(f"('user{i}', '123456', '{phone}', 1, 0)")
    
    # 每100个一批
    batch_size = 100
    for i in range(0, len(values), batch_size):
        batch = values[i:i + batch_size]
        if i == 0:
            sql_lines.append(",\n".join(batch) + (";" if i + batch_size >= len(values) else ","))
        else:
            sql_lines.append("")
            sql_lines.append("INSERT INTO t_user (username, password, phone, status, deleted) VALUES")
            sql_lines.append(",\n".join(batch) + ";")
    
    sql_lines.extend([
        "",
        "-- 设置商品库存为100（测试超卖场景）",
        "UPDATE t_product SET",
        "    available_stock = 100,",
        "    total_stock = 100,",
        "    status = 1,",
        "    start_time = NOW(),",
        "    end_time = DATE_ADD(NOW(), INTERVAL 24 HOUR)",
        "WHERE id = 1;",
        "",
        "-- 验证结果",
        "SELECT '用户总数' as info, COUNT(*) as count FROM t_user;",
        "SELECT id, product_name, available_stock, status FROM t_product WHERE id = 1;"
    ])
    
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("\n".join(sql_lines))
    
    print(f"✅ 已生成SQL文件: {output_file}")
    print(f"   用户数量: {count}")
    print(f"\n执行命令:")
    print(f"   mysql -u root -p < {output_file}")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="生成批量用户SQL")
    parser.add_argument("--count", type=int, default=500, help="用户数量")
    parser.add_argument("--output", type=str, default="insert_users.sql", help="输出文件")
    args = parser.parse_args()
    
    generate_users_sql(args.count, args.output)

