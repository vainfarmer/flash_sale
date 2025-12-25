-- 秒杀压测用户初始化脚本
-- 创建500个测试用户

USE flash_sale;

-- 清空现有测试用户（保留admin）
DELETE FROM t_user WHERE username LIKE 'user%';

-- 批量插入500个用户
INSERT INTO t_user (username, password, phone, status, deleted) VALUES
('user1', '123456', '13800000001', 1, 0),
('user2', '123456', '13800000002', 1, 0),
('user3', '123456', '13800000003', 1, 0),
('user4', '123456', '13800000004', 1, 0),
('user5', '123456', '13800000005', 1, 0),
('user6', '123456', '13800000006', 1, 0),
('user7', '123456', '13800000007', 1, 0),
('user8', '123456', '13800000008', 1, 0),
('user9', '123456', '13800000009', 1, 0),
('user10', '123456', '13800000010', 1, 0);

-- 使用生成序列批量插入更多用户
INSERT INTO t_user (username, password, phone, status, deleted)
SELECT 
    CONCAT('user', seq.n) as username,
    '123456' as password,
    CONCAT('138', LPAD(seq.n, 8, '0')) as phone,
    1 as status,
    0 as deleted
FROM (
    SELECT @row := @row + 1 as n
    FROM (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
         (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
         (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t3,
         (SELECT @row := 10) t4
    LIMIT 490
) seq
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = CONCAT('user', seq.n));

-- 设置秒杀商品库存（100件，用于测试超卖场景）
UPDATE t_product SET 
    available_stock = 100, 
    total_stock = 100,
    status = 1,
    start_time = NOW(),
    end_time = DATE_ADD(NOW(), INTERVAL 24 HOUR)
WHERE id = 1;

-- 验证
SELECT '用户总数:' as info, COUNT(*) as count FROM t_user;
SELECT '商品库存:' as info, id, product_name, available_stock FROM t_product WHERE id = 1;

