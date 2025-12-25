package org.example.flash_sale.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * 简单健康检查 - 只检查应用是否运行
     */
    @GetMapping("/health")
    public Result<String> health() {
       log.info("测试连接");
      return Result.success("OK");
    }
}
