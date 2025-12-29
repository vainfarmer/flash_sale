package org.example.flash_sale.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.service.StockConsistencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存一致性检查定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockConsistencyScheduler {

    private final StockConsistencyService consistencyService;

    /**
     * 是否开启自动修复
     */
    @Value("${flash-sale.consistency.auto-repair:false}")
    private boolean autoRepair;

    /**
     * 定时检查库存一致性（每5分钟执行一次）
     * 可通过配置 flash-sale.consistency.check-cron 修改
     */
    @Scheduled(cron = "${flash-sale.consistency.check-cron:0 */5 * * * ?}")
    public void checkConsistency() {
        log.info("【定时任务】开始执行库存一致性检查...");
        
        try {
            consistencyService.checkAllProductsConsistency();
            
            if (autoRepair) {
                log.info("【定时任务】自动修复已开启，开始修复不一致...");
                consistencyService.repairAllConsistency();
            }
        } catch (Exception e) {
            log.error("【定时任务】库存一致性检查失败", e);
        }
        
        log.info("【定时任务】库存一致性检查完成");
    }
}

