-- 秒杀系统数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS flash_sale DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE flash_sale;

-- 用户表
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_username (username),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 秒杀商品表
DROP TABLE IF EXISTS t_product;
CREATE TABLE t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    description TEXT COMMENT '商品描述',
    image_url VARCHAR(500) COMMENT '商品图片',
    original_price DECIMAL(10,2) NOT NULL COMMENT '原价',
    flash_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    total_stock INT NOT NULL COMMENT '总库存',
    available_stock INT NOT NULL COMMENT '剩余库存',
    start_time DATETIME NOT NULL COMMENT '秒杀开始时间',
    end_time DATETIME NOT NULL COMMENT '秒杀结束时间',
    limit_per_user INT DEFAULT 1 COMMENT '每人限购数量',
    status TINYINT DEFAULT 0 COMMENT '活动状态：0-未开始，1-进行中，2-已结束',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_start_time (start_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

-- 订单表
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(200) COMMENT '商品名称（冗余字段）',
    quantity INT NOT NULL DEFAULT 1 COMMENT '购买数量',
    amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    status TINYINT DEFAULT 0 COMMENT '订单状态：0-待支付，1-已支付，2-已取消，3-已退款',
    pay_time DATETIME COMMENT '支付时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

-- 插入测试用户
INSERT INTO t_user (username, password, phone, status) VALUES
('user1', '123456', '13800138001', 1),
('user2', '123456', '13800138002', 1),
('user3', '123456', '13800138003', 1),
('admin', 'admin123', '13800138000', 1);

-- 插入测试秒杀商品
INSERT INTO t_product (product_name, description, original_price, flash_price, total_stock, available_stock, start_time, end_time, limit_per_user, status) VALUES
('iPhone 15 Pro Max', '苹果最新旗舰手机，A17 Pro芯片，钛金属边框', 9999.00, 7999.00, 100, 100, NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), 1, 1),
('MacBook Pro 14寸', 'M3 Pro芯片，18GB统一内存，512GB存储', 16999.00, 14999.00, 50, 50, NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), 1, 1),
('AirPods Pro 2', '主动降噪，自适应透明模式，MagSafe充电盒', 1899.00, 1499.00, 200, 200, NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), 2, 1),
('Apple Watch Ultra 2', '钛金属表壳，超长续航，双频GPS', 6499.00, 5499.00, 30, 30, NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), 1, 1),
('iPad Pro 12.9寸', 'M2芯片，Liquid Retina XDR显示屏', 10999.00, 8999.00, 80, 80, DATE_ADD(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 25 HOUR), 1, 0);

-- 查看插入的数据
SELECT * FROM t_user;
SELECT * FROM t_product;

