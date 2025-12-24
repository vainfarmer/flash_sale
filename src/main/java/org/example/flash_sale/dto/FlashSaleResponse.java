package org.example.flash_sale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 订单号（秒杀成功时返回）
     */
    private String orderNo;

    public static FlashSaleResponse success(String orderNo) {
        return FlashSaleResponse.builder()
                .success(true)
                .code(200)
                .message("秒杀成功，请尽快完成支付")
                .orderNo(orderNo)
                .build();
    }

    public static FlashSaleResponse fail(Integer code, String message) {
        return FlashSaleResponse.builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }
}

