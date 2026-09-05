package com.example.orderservice.dto;

import com.example.orderservice.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderResponseDTO {
    private Long id;
    private Long customerId;
    private Long productId;
    private Integer quantity;
    private BigDecimal productPrice;
    private BigDecimal totalAmount;
    private LocalDateTime orderDate;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Thông tin mở rộng
    private CustomerResponseDTO customer;
    private ProductResponseDTO product;

    // Thông báo cho client
    private String message;
}