package org.example.flash_sale.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.flash_sale.common.Constants;
import org.example.flash_sale.common.Result;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 限流过滤器
 * 1. IP限流：防止恶意攻击
 * 2. 黑名单拦截：封禁恶意IP
 */
@Slf4j
@Component
@WebFilter(urlPatterns = "/api/flash/*")
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * IP限流：每秒最大请求数
     */
    private static final int IP_LIMIT_PER_SECOND = 50;

    /**
     * IP限流时间窗口（秒）
     */
    private static final int IP_LIMIT_WINDOW = 1;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();
        
        // 只对秒杀接口进行限流
        if (!uri.startsWith("/api/flash")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);

        // 1. 检查黑名单
        if (isInBlacklist(clientIp)) {
            log.warn("IP {} 在黑名单中，拒绝访问", clientIp);
            writeResponse(httpResponse, Result.fail(Constants.CODE_USER_BLOCKED, "您的IP已被限制访问"));
            return;
        }

        // 2. IP限流检查
        if (!checkIpRateLimit(clientIp)) {
            log.warn("IP {} 请求过于频繁", clientIp);
            writeResponse(httpResponse, Result.fail(Constants.CODE_REQUEST_TOO_FAST, "请求过于频繁，请稍后再试"));
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 检查IP是否在黑名单中
     */
    private boolean isInBlacklist(String ip) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(Constants.REDIS_BLACKLIST_KEY, ip));
    }

    /**
     * IP限流检查（滑动窗口计数器）
     */
    private boolean checkIpRateLimit(String ip) {
        String key = Constants.REDIS_IP_LIMIT_KEY + ip;
        
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 第一次请求，设置过期时间
            redisTemplate.expire(key, IP_LIMIT_WINDOW, TimeUnit.SECONDS);
        }
        
        return count == null || count <= IP_LIMIT_PER_SECOND;
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理的情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 写入响应
     */
    private void writeResponse(HttpServletResponse response, Result<?> result) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}

