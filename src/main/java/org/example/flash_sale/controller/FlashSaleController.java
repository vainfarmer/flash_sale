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
}

