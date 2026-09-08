package com.example.orderservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j  // Thêm annotation này để có log
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponseError> handleOrderNotFound(
            OrderNotFoundException ex) {
        ApiResponseError error = new ApiResponseError(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseError> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ApiResponseError error = new ApiResponseError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                message
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseError> handleIllegalArgumentException(
            IllegalArgumentException ex) {
        ApiResponseError error = new ApiResponseError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Xử lý lỗi khi không kết nối được Product Service
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponseError> handleResourceAccessException(
            ResourceAccessException ex) {
        log.error("Service connection error: {}", ex.getMessage());

        ApiResponseError error = new ApiResponseError(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Unavailable",
                "Product service is currently unavailable. Please try again later."
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    // Xử lý lỗi 5xx từ Product Service
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ApiResponseError> handleHttpServerErrorException(
            HttpServerErrorException ex) {
        log.error("Product service error: {}", ex.getMessage());

        String message = "Product service is experiencing issues. ";
        if (ex.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
            message += "Please try again later.";
        } else {
            message += "Please try again later.";
        }

        ApiResponseError error = new ApiResponseError(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Error",
                message
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    private static final String PRODUCT_SERVICE_DOWN_MSG =
            "Hệ thống đang quá tải, yêu cầu của bạn đã được ghi nhận nhưng chưa thể hoàn tất kiểm tra kho. Vui lòng quay lại sau 1 phút.";

    @ExceptionHandler(ProductServiceUnavailableException.class)
    public ResponseEntity<ApiResponseError> handleProductServiceUnavailable(
            ProductServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponseError(
                        LocalDateTime.now(),
                        503,
                        "Service Unavailable",
                        PRODUCT_SERVICE_DOWN_MSG
                ));
    }

    // Xử lý lỗi RuntimeException (bao gồm cả lỗi từ ServiceClient)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseError> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage());

        int status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        String errorType = "Internal Server Error";
        String message = ex.getMessage();

        // Kiểm tra nếu là lỗi "not found"
        if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
            status = HttpStatus.NOT_FOUND.value();
            errorType = "Not Found";
        }

        ApiResponseError error = new ApiResponseError(
                LocalDateTime.now(),
                status,
                errorType,
                message
        );
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseError> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        ApiResponseError error = new ApiResponseError(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}