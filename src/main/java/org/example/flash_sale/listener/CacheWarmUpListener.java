package org.example.flash_sale.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.service.ProductService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 缓存预热监听器
 * 在应用启动完成后自动预热秒杀商品缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmUpListener {

    private final ProductService productService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("======== 应用启动完成，开始预热缓存 ========");
        try {
            productService.warmUpAllProducts();
            log.info("======== 缓存预热完成 ========");
        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }
}

