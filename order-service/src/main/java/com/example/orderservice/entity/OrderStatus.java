package com.example.orderservice.entity;

public enum OrderStatus {
    PENDING,           // Đang xử lý
    PENDING_REVIEW,    // Chờ xem xét (khi dùng fallback)
    CONFIRMED,         // Đã xác nhận
    SHIPPING,          // Đang giao
    DELIVERED,         // Đã giao
    CANCELLED          // Đã hủy
}