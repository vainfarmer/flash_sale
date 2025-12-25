-- 批量插入测试用户（500个用户）
-- 用户名格式：user1 ~ user500，密码统一为：123456

USE flash_sale;

-- 清空现有用户（保留管理员）
DELETE FROM t_user WHERE username != 'admin';

-- 批量插入用户
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS batch_insert_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 500 DO
        INSERT INTO t_user (username, password, phone, status, deleted)
        VALUES (
            CONCAT('user', i),
            '123456',
            CONCAT('138', LPAD(i, 8, '0')),
            1,
            0
        );
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

-- 执行存储过程
CALL batch_insert_users();

-- 删除存储过程
DROP PROCEDURE IF EXISTS batch_insert_users;

-- 或者直接用INSERT语句批量插入（更简单）
-- 先删除上面的存储过程方式，用下面的批量INSERT

-- 验证插入结果
SELECT COUNT(*) as user_count FROM t_user;
SELECT * FROM t_user LIMIT 10;

-- 更新商品库存为较小值以便测试（比如100件）
UPDATE t_product SET available_stock = 100, total_stock = 100 WHERE id = 1;

-- 查看商品库存
SELECT id, product_name, available_stock, total_stock FROM t_product;

