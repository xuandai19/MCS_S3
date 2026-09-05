package com.example.orderservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CustomerResponseDTO {
    private Long id;
    private String fullName;
    private String email;
    private String address;
    private LocalDateTime createdAt;
}