package org.example.flash_sale.service;

import org.example.flash_sale.dto.FlashSaleRequest;
import org.example.flash_sale.dto.FlashSaleResponse;

import java.util.concurrent.CompletableFuture;

/**
 * 秒杀服务接口
 */
public interface FlashSaleService {

    /**
     * 执行秒杀（Redis + Lua + Kafka 方案）
     * 
     * @param userId  用户ID
     * @param request 秒杀请求
     * @return 秒杀结果
     */
    FlashSaleResponse doFlashSale(Long userId, FlashSaleRequest request);

    /**
     * 异步执行秒杀（使用虚拟线程）
     * 释放Tomcat线程，提高并发处理能力
     * 
     * @param userId  用户ID
     * @param request 秒杀请求
     * @return 秒杀结果的Future
     */
    CompletableFuture<FlashSaleResponse> doFlashSaleAsync(Long userId, FlashSaleRequest request);

    /**
     * 执行秒杀（直接操作数据库方案，用于性能对比）
     * 
     * @param userId  用户ID
     * @param request 秒杀请求
     * @return 秒杀结果
     */
    FlashSaleResponse doFlashSaleDirect(Long userId, FlashSaleRequest request);

    /**
     * 异步执行秒杀（直接操作数据库，使用虚拟线程）
     * 
     * @param userId  用户ID
     * @param request 秒杀请求
     * @return 秒杀结果的Future
     */
    CompletableFuture<FlashSaleResponse> doFlashSaleDirectAsync(Long userId, FlashSaleRequest request);

    /**
     * 检查秒杀活动状态
     */
    boolean checkActivityStatus(Long productId);
}
