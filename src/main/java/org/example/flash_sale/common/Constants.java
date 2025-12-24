package org.example.flash_sale.common;

/**
 * 系统常量
 */
public class Constants {

    private Constants() {
    }

    // ==================== Redis Key 前缀 ====================
    
    /**
     * 商品库存Key前缀
     */
    public static final String REDIS_STOCK_KEY = "flash:stock:";

    /**
     * 商品信息Key前缀
     */
    public static final String REDIS_PRODUCT_KEY = "flash:product:";

    /**
     * 用户购买记录Key前缀（防止重复购买）
     */
    public static final String REDIS_USER_BOUGHT_KEY = "flash:bought:";

    /**
     * IP限流Key前缀
     */
    public static final String REDIS_IP_LIMIT_KEY = "flash:ip:limit:";

    /**
     * 用户限流Key前缀
     */
    public static final String REDIS_USER_LIMIT_KEY = "flash:user:limit:";

    /**
     * 黑名单Key
     */
    public static final String REDIS_BLACKLIST_KEY = "flash:blacklist";

    /**
     * 秒杀活动信息Key
     */
    public static final String REDIS_ACTIVITY_KEY = "flash:activity:";

    // ==================== Kafka Topic ====================

    /**
     * 订单消息Topic
     */
    public static final String KAFKA_TOPIC_ORDER = "flash-sale-order";

    // ==================== 业务状态码 ====================

    /**
     * 活动未开始
     */
    public static final int CODE_ACTIVITY_NOT_START = 1001;

    /**
     * 活动已结束
     */
    public static final int CODE_ACTIVITY_END = 1002;

    /**
     * 库存不足
     */
    public static final int CODE_STOCK_NOT_ENOUGH = 1003;

    /**
     * 重复购买
     */
    public static final int CODE_REPEAT_BUY = 1004;

    /**
     * 请求太频繁
     */
    public static final int CODE_REQUEST_TOO_FAST = 1005;

    /**
     * 用户被禁止
     */
    public static final int CODE_USER_BLOCKED = 1006;

    /**
     * 未登录
     */
    public static final int CODE_NOT_LOGIN = 1007;

    /**
     * 系统繁忙
     */
    public static final int CODE_SYSTEM_BUSY = 1008;

    // ==================== 订单状态 ====================

    /**
     * 待支付
     */
    public static final int ORDER_STATUS_PENDING = 0;

    /**
     * 已支付
     */
    public static final int ORDER_STATUS_PAID = 1;

    /**
     * 已取消
     */
    public static final int ORDER_STATUS_CANCELLED = 2;

    /**
     * 已退款
     */
    public static final int ORDER_STATUS_REFUNDED = 3;

    // ==================== 活动状态 ====================

    /**
     * 未开始
     */
    public static final int ACTIVITY_STATUS_NOT_START = 0;

    /**
     * 进行中
     */
    public static final int ACTIVITY_STATUS_RUNNING = 1;

    /**
     * 已结束
     */
    public static final int ACTIVITY_STATUS_END = 2;
}

