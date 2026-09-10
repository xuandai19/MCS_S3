package com.example.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. Lấy thông tin request
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        String queryParams = exchange.getRequest().getURI().getQuery();
        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        String timestamp = LocalDateTime.now().format(FORMATTER);

        // 2. In log "Incoming request to: {path}"
        log.info("========================================");
        log.info("⏰ Time       : {}", timestamp);
        log.info("📥 Incoming request to: {}", path);
        log.info("🔧 Method     : {}", method);
        if (queryParams != null && !queryParams.isEmpty()) {
            log.info("❓ Query      : {}", queryParams);
        }
        log.info("🌐 Client IP  : {}", clientIp);
        log.info("========================================");

        // 3. Ghi lại thời gian bắt đầu
        long startTime = System.currentTimeMillis();

        // 4. Cho phép request đi tiếp đến Service đích
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    // 5. Log sau khi response trả về
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;

                    log.info("✅ Response for: {} | Status: {} | Duration: {}ms",
                            path, statusCode, duration);
                }));
    }

    @Override
    public int getOrder() {
        // Đặt order cao để filter này chạy đầu tiên
        // Số càng nhỏ thì ưu tiên càng cao
        return Ordered.HIGHEST_PRECEDENCE;
    }
}