-- =============================================
-- 秒杀压测用户初始化脚本
-- 功能：批量插入指定数量的测试用户
-- 用法：修改 @user_count 变量值，然后执行此脚本
-- =============================================

USE flash_sale;

-- ========== 配置区域 ==========
-- 修改这里的数值来指定要创建的用户数量
SET @user_count = 500;
-- ==============================

-- 清空现有测试用户（保留admin）
DELETE FROM t_user WHERE username LIKE 'user%';

-- 删除已存在的存储过程
DROP PROCEDURE IF EXISTS batch_insert_users;

-- 创建批量插入用户的存储过程
DELIMITER //
CREATE PROCEDURE batch_insert_users(IN total_count INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    
    -- 开启事务提高插入效率
    START TRANSACTION;
    
    WHILE i <= total_count DO
        INSERT INTO t_user (username, password, phone, status, deleted)
        VALUES (
            CONCAT('user', i),
            '123456',
            CONCAT('138', LPAD(i, 8, '0')),
            1,
            0
        );
        SET i = i + 1;
        
        -- 每1000条提交一次，避免事务过大
        IF i % 1000 = 0 THEN
            COMMIT;
            START TRANSACTION;
        END IF;
    END WHILE;
    
    COMMIT;
END //
DELIMITER ;

-- 执行存储过程，插入指定数量的用户
CALL batch_insert_users(@user_count);

-- 删除存储过程（清理）
DROP PROCEDURE IF EXISTS batch_insert_users;

-- 设置秒杀商品库存（100件，用于测试超卖场景）
UPDATE t_product SET 
    available_stock = 100, 
    total_stock = 100,
    status = 1,
    start_time = NOW(),
    end_time = DATE_ADD(NOW(), INTERVAL 24 HOUR)
WHERE id = 1;

-- ========== 验证结果 ==========
SELECT '✅ 初始化完成!' as message;
SELECT CONCAT('用户总数: ', COUNT(*)) as info FROM t_user WHERE username LIKE 'user%';
SELECT CONCAT('商品库存: ', available_stock) as info FROM t_product WHERE id = 1;
