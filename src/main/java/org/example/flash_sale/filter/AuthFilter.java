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
import org.example.flash_sale.config.JwtConfig;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 身份验证过滤器
 * 验证Token有效性，提取用户信息
 */
@Slf4j
@Component
@WebFilter(urlPatterns = "/api/flash/*")
@Order(2)
@RequiredArgsConstructor
public class AuthFilter implements Filter {

    private final JwtConfig jwtConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 不需要认证的接口
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/flash/products",   // 商品列表
            "/api/flash/product/",   // 商品详情
            "/api/flash/test/",      // 压测接口（生产环境删除）
            "/api/flash/admin/",     // 管理接口
            "/api/admin/",           // 管理接口（订单超时等）
            "/api/user/login",       // 登录
            "/api/user/register"     // 注册
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();

        // 白名单放行
        if (isWhiteListed(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // 只对秒杀接口进行认证
        if (!uri.startsWith("/api/flash")) {
            chain.doFilter(request, response);
            return;
        }

        // 获取Token
        String token = getTokenFromRequest(httpRequest);
        if (token == null || token.isEmpty()) {
            log.warn("请求缺少Token: {}", uri);
            writeResponse(httpResponse, Result.fail(Constants.CODE_NOT_LOGIN, "请先登录"));
            return;
        }

        // 验证Token
        if (!jwtConfig.validateToken(token)) {
            log.warn("Token无效或已过期");
            writeResponse(httpResponse, Result.fail(Constants.CODE_NOT_LOGIN, "登录已过期，请重新登录"));
            return;
        }

        // 提取用户ID，放入请求属性
        try {
            Long userId = jwtConfig.getUserIdFromToken(token);
            httpRequest.setAttribute("userId", userId);
            log.debug("用户 {} 通过认证", userId);
        } catch (Exception e) {
            log.error("解析Token失败", e);
            writeResponse(httpResponse, Result.fail(Constants.CODE_NOT_LOGIN, "登录信息无效"));
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 从请求中获取Token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // 也支持从请求参数获取
        return request.getParameter("token");
    }

    /**
     * 检查是否在白名单中
     */
    private boolean isWhiteListed(String uri) {
        return WHITE_LIST.stream().anyMatch(uri::startsWith);
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

