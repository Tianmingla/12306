package com.lalal.modules.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret:12306-secret-key-12306-secret-key-12306-secret-key}")
    private String secret;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip authentication for login / 短信验证码 / 支付宝回调 / Admin登录
        if (path.contains("/api/user/login")
                || path.contains("/api/user/register")
                || path.contains("/api/user/sms/send")
                || path.contains("/api/order/pay/notify")
                || path.contains("/api/order/pay/return")
                || path.contains("/api/admin/auth/login")) {
            return chain.filter(exchange);
        }

        // Check if the request is for an API
        if (path.startsWith("/api/")) {
            String token = extractToken(request);
            if (token == null || !validateToken(token)) {
                return unauthorized(exchange);
            }

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(getSigningKey())
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                // 获取 Token 类型
                String tokenType = claims.get("type", String.class);

                // Admin 接口需要 ADMIN 类型 Token
                if (path.startsWith("/api/admin/")) {
                    if (!"ADMIN".equals(tokenType)) {
                        return unauthorized(exchange);
                    }
                    // 传递 Admin 用户信息
                    Object uid = claims.get("uid");
                    String username = claims.getSubject();
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-Admin-Id", uid != null ? uid.toString() : "")
                            .header("X-Admin-Name", username != null ? username : "")
                            .header("X-User-Type", "ADMIN")
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                }

                // 普通用户接口
                String phone = claims.getSubject();
                Object uid = claims.get("uid");
                ServerHttpRequest.Builder mutate = request.mutate()
                        .header("X-User-Name", phone != null ? phone : "")
                        .header("X-User-Type", "USER");
                if (uid != null) {
                    mutate.header("X-User-Id", uid.toString());
                }
                ServerHttpRequest mutatedRequest = mutate.build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (Exception e) {
                return unauthorized(exchange);
            }
        }

        return chain.filter(exchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return -100; // Run before other filters
    }
}
