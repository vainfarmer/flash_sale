package org.example.flash_sale.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Result;
import org.example.flash_sale.entity.Product;
import org.example.flash_sale.mapper.ProductMapper;
import org.example.flash_sale.service.StockConsistencyService;
import org.example.flash_sale.service.StockConsistencyService.ConsistencyCheckResult;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存一致性检验管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/consistency")
@RequiredArgsConstructor
public class ConsistencyController {

    private final StockConsistencyService consistencyService;
    private final ProductMapper productMapper;

    /**
     * 检查单个商品的库存一致性
     */
    @GetMapping("/check/{productId}")
    public Result<ConsistencyCheckResult> checkSingle(@PathVariable Long productId) {
        ConsistencyCheckResult result = consistencyService.checkConsistency(productId);
        return Result.success(result);
    }

    /**
     * 检查所有商品的库存一致性
     */
    @GetMapping("/check-all")
    public Result<Map<String, Object>> checkAll() {
        List<Product> products = productMapper.selectList(null);
        List<ConsistencyCheckResult> results = new ArrayList<>();
        int consistentCount = 0;
        int inconsistentCount = 0;

        for (Product product : products) {
            ConsistencyCheckResult result = consistencyService.checkConsistency(product.getId());
            results.add(result);
            if (result.isConsistent()) {
                consistentCount++;
            } else {
                inconsistentCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("total", products.size());
        response.put("consistent", consistentCount);
        response.put("inconsistent", inconsistentCount);
        response.put("details", results);

        return Result.success(response);
    }

    /**
     * 修复单个商品的库存一致性（以数据库为准）
     */
    @PostMapping("/repair/{productId}")
    public Result<String> repairSingle(
            @PathVariable Long productId,
            @RequestParam(value = "useDbAsSource", defaultValue = "true") boolean useDbAsSource) {
        
        // 先检查
        ConsistencyCheckResult beforeResult = consistencyService.checkConsistency(productId);
        if (beforeResult.isConsistent()) {
            return Result.success("库存已经一致，无需修复");
        }

        // 执行修复
        consistencyService.repairConsistency(productId, useDbAsSource);

        // 验证修复结果
        ConsistencyCheckResult afterResult = consistencyService.checkConsistency(productId);
        
        String message = String.format("修复完成（以%s为准）。修复前: %s, 修复后: %s",
                useDbAsSource ? "数据库" : "Redis",
                beforeResult.message(),
                afterResult.message());

        return Result.success(message);
    }

    /**
     * 修复所有商品的库存一致性（以数据库为准）
     */
    @PostMapping("/repair-all")
    public Result<Map<String, Object>> repairAll() {
        List<Product> products = productMapper.selectList(null);
        List<Map<String, Object>> repairResults = new ArrayList<>();
        int repairedCount = 0;

        for (Product product : products) {
            ConsistencyCheckResult beforeResult = consistencyService.checkConsistency(product.getId());
            if (!beforeResult.isConsistent()) {
                consistencyService.repairConsistency(product.getId(), true);
                ConsistencyCheckResult afterResult = consistencyService.checkConsistency(product.getId());

                Map<String, Object> repairResult = new HashMap<>();
                repairResult.put("productId", product.getId());
                repairResult.put("productName", product.getProductName());
                repairResult.put("before", beforeResult.message());
                repairResult.put("after", afterResult.message());
                repairResult.put("repaired", afterResult.isConsistent());
                repairResults.add(repairResult);

                repairedCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalProducts", products.size());
        response.put("repairedCount", repairedCount);
        response.put("details", repairResults);

        return Result.success(response);
    }

    /**
     * 获取库存对比报告
     */
    @GetMapping("/report")
    public Result<List<Map<String, Object>>> getReport() {
        List<Product> products = productMapper.selectList(null);
        List<Map<String, Object>> report = new ArrayList<>();

        for (Product product : products) {
            ConsistencyCheckResult result = consistencyService.checkConsistency(product.getId());
            
            Map<String, Object> item = new HashMap<>();
            item.put("productId", result.productId());
            item.put("productName", result.productName());
            item.put("dbStock", result.dbStock());
            item.put("redisStock", result.redisStock());
            item.put("stockDiff", result.dbStock() != null && result.redisStock() != null 
                    ? result.dbStock() - result.redisStock() : null);
            item.put("dbSoldCount", result.dbSoldCount());
            item.put("redisSoldCount", result.redisSoldCount());
            item.put("soldDiff", result.dbSoldCount() != null && result.redisSoldCount() != null 
                    ? result.dbSoldCount() - result.redisSoldCount() : null);
            item.put("isConsistent", result.isConsistent());
            item.put("message", result.message());
            
            report.add(item);
        }

        return Result.success(report);
    }
}

