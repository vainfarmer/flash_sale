package org.example.flash_sale.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Result;
import org.example.flash_sale.dto.FlashSaleRequest;
import org.example.flash_sale.dto.FlashSaleResponse;
import org.example.flash_sale.entity.Product;
import org.example.flash_sale.service.FlashSaleService;
import org.example.flash_sale.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 秒杀控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/flash")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;
    private final ProductService productService;

    /**
     * 获取秒杀商品列表
     */
    @GetMapping("/products")
    public Result<List<Product>> getProducts() {
        List<Product> products = productService.getAllFlashProducts();
        return Result.success(products);
    }

    /**
     * 获取商品详情
     */
    @GetMapping("/product/{id}")
    public Result<Product> getProduct(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            return Result.fail(404, "商品不存在");
        }
        return Result.success(product);
    }

    /**
     * 检查活动状态
     */
    @GetMapping("/check/{productId}")
    public Result<Boolean> checkActivity(@PathVariable Long productId) {
        boolean active = flashSaleService.checkActivityStatus(productId);
        return Result.success(active);
    }

    /**
     * 执行秒杀
     */
    @PostMapping("/do")
    public Result<FlashSaleResponse> doFlashSale(
            @Valid @RequestBody FlashSaleRequest request,
            HttpServletRequest httpRequest) {

        // 从过滤器中获取用户ID
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }

        FlashSaleResponse response = flashSaleService.doFlashSale(userId, request);

        if (response.getSuccess()) {
            return Result.success(response);
        } else {
            return Result.fail(response.getCode(), response.getMessage());
        }
    }

    /**
     * 预热缓存（管理员接口）
     */
    @PostMapping("/admin/warmup")
    public Result<String> warmUpCache() {
        productService.warmUpAllProducts();
        return Result.success("缓存预热完成");
    }

    /**
     * 预热单个商品缓存
     */
    @PostMapping("/admin/warmup/{productId}")
    public Result<String> warmUpProduct(@PathVariable Long productId) {
        productService.warmUpProductCache(productId);
        return Result.success("商品缓存预热完成");
    }

    /**
     * 压测专用接口 - Redis + Lua + Kafka 方案
     * 注意：生产环境应删除此接口！
     */
    @PostMapping("/test/do")
    public Result<FlashSaleResponse> doFlashSaleForTest(
            @RequestParam("userId") Long userId,
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity) {

        if (userId == null || productId == null) {
            return Result.fail(400, "参数不完整");
        }

        FlashSaleRequest request = new FlashSaleRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);

        FlashSaleResponse response = flashSaleService.doFlashSale(userId, request);

        if (response.getSuccess()) {
            return Result.success(response);
        } else {
            return Result.fail(response.getCode(), response.getMessage());
        }
    }

    /**
     * 压测专用接口 - 直接操作数据库方案（用于性能对比）
     * 不使用Redis缓存、Lua脚本、Kafka
     * 注意：生产环境应删除此接口！
     */
    @PostMapping("/test/do-direct")
    public Result<FlashSaleResponse> doFlashSaleDirectForTest(
            @RequestParam("userId") Long userId,
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity) {

        if (userId == null || productId == null) {
            return Result.fail(400, "参数不完整");
        }

        FlashSaleRequest request = new FlashSaleRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);

        FlashSaleResponse response = flashSaleService.doFlashSaleDirect(userId, request);

        if (response.getSuccess()) {
            return Result.success(response);
        } else {
            return Result.fail(response.getCode(), response.getMessage());
        }
    }

    // ==================== 异步接口（使用虚拟线程）====================

    /**
     * 异步秒杀接口 - Redis + Lua + Kafka 方案（虚拟线程）
     * 释放Tomcat线程，提高并发处理能力
     */
    @PostMapping("/test/do-async")
    public CompletableFuture<Result<FlashSaleResponse>> doFlashSaleAsync(
            @RequestParam("userId") Long userId,
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity) {

        if (userId == null || productId == null) {
            return CompletableFuture.completedFuture(Result.fail(400, "参数不完整"));
        }

        FlashSaleRequest request = new FlashSaleRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);

        return flashSaleService.doFlashSaleAsync(userId, request)
                .thenApply(response -> {
                    if (response.getSuccess()) {
                        return Result.success(response);
                    } else {
                        return Result.fail(response.getCode(), response.getMessage());
                    }
                });
    }

    /**
     * 异步秒杀接口 - 直接数据库方案（虚拟线程）
     */
    @PostMapping("/test/do-direct-async")
    public CompletableFuture<Result<FlashSaleResponse>> doFlashSaleDirectAsync(
            @RequestParam("userId") Long userId,
            @RequestParam("productId") Long productId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity) {

        if (userId == null || productId == null) {
            return CompletableFuture.completedFuture(Result.fail(400, "参数不完整"));
        }

        FlashSaleRequest request = new FlashSaleRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);

        return flashSaleService.doFlashSaleDirectAsync(userId, request)
                .thenApply(response -> {
                    if (response.getSuccess()) {
                        return Result.success(response);
                    } else {
                        return Result.fail(response.getCode(), response.getMessage());
                    }
                });
    }
}
