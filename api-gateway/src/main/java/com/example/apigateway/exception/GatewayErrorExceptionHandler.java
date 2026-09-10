package com.example.apigateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Order(-1)  // Chạy trước các ErrorWebExceptionHandler mặc định của Spring
@Slf4j
@RequiredArgsConstructor
public class GatewayErrorExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Nếu response đã được commit, không thể ghi lại
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // Lấy thông tin request
        String path = exchange.getRequest().getURI().getPath();
        String requestId = UUID.randomUUID().toString().substring(0, 8);

        // Xác định status code và message
        HttpStatus status = determineHttpStatus(ex);
        String errorTitle = determineErrorTitle(status);
        String message = determineErrorMessage(ex, status);

        // Log lỗi
        log.error("[{}] Gateway Error | Path: {} | Status: {} | Message: {}",
                requestId, path, status.value(), message, ex);

        // Tạo đối tượng lỗi
        ApiResponseError apiError = ApiResponseError.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(errorTitle)
                .message(message)
                .path(path)
                .requestId(requestId)
                .build();

        // Set response headers
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Ghi body vào response
        return writeResponse(response, apiError);
    }

    private HttpStatus determineHttpStatus(Throwable ex) {
        // Lỗi kết nối đến Service đích (Service sập)
        if (ex instanceof java.net.ConnectException
                || ex instanceof java.net.UnknownHostException
                || ex.getClass().getName().contains("ConnectException")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }

        // Lỗi từ LoadBalancer - không tìm thấy instance
        if (ex.getClass().getName().contains("NotFoundException")
                || ex.getMessage() != null
                && ex.getMessage().contains("Unable to find instance")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }

        // Lỗi timeout
        if (ex instanceof java.util.concurrent.TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }

        // Lỗi ResponseStatusException
        if (ex instanceof ResponseStatusException) {
            return HttpStatus.valueOf(((ResponseStatusException) ex)
                    .getStatusCode().value());
        }

        // Mặc định: 500 Internal Server Error
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String determineErrorTitle(HttpStatus status) {
        switch (status) {
            case SERVICE_UNAVAILABLE:
                return "Service Unavailable";
            case GATEWAY_TIMEOUT:
                return "Gateway Timeout";
            case NOT_FOUND:
                return "Not Found";
            case BAD_GATEWAY:
                return "Bad Gateway";
            default:
                return "Internal Server Error";
        }
    }

    private String determineErrorMessage(Throwable ex, HttpStatus status) {
        switch (status) {
            case SERVICE_UNAVAILABLE:
                return "Cổng Gateway không thể kết nối tới dịch vụ đích";
            case GATEWAY_TIMEOUT:
                return "Dịch vụ đích phản hồi quá lâu, vui lòng thử lại sau";
            case NOT_FOUND:
                return "Không tìm thấy đường dẫn yêu cầu";
            case BAD_GATEWAY:
                return "Lỗi kết nối tới dịch vụ đích";
            default:
                String msg = ex.getMessage();
                return msg != null ? msg : "Đã xảy ra lỗi không xác định";
        }
    }

    private Mono<Void> writeResponse(ServerHttpResponse response, ApiResponseError apiError) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(apiError);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error writing JSON response: {}", e.getMessage());
            byte[] fallback = ("{\"status\":500,\"error\":\"Internal Server Error\"," +
                    "\"message\":\"Lỗi xử lý response\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(fallback);
            return response.writeWith(Mono.just(buffer));
        }
    }
}