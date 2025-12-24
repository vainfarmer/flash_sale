package org.example.flash_sale.service;

import org.example.flash_sale.dto.FlashSaleRequest;
import org.example.flash_sale.dto.FlashSaleResponse;

/**
 * 秒杀服务接口
 */
public interface FlashSaleService {

    /**
     * 执行秒杀
     * @param userId 用户ID
     * @param request 秒杀请求
     * @return 秒杀结果
     */
    FlashSaleResponse doFlashSale(Long userId, FlashSaleRequest request);

    /**
     * 检查秒杀活动状态
     */
    boolean checkActivityStatus(Long productId);
}

