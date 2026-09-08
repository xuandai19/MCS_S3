package com.example.orderservice.service;

import com.example.orderservice.client.ServiceClient;
import com.example.orderservice.dto.*;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.exception.ProductServiceUnavailableException;
import com.example.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ServiceClient serviceClient;

    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO request) {
        log.info("Creating order for customerId: {}, productId: {}",
                request.getCustomerId(), request.getProductId());

        // 1. Kiểm tra customer
        CustomerResponseDTO customer = serviceClient.getCustomerById(request.getCustomerId());
        log.info("Customer found: {}", customer.getFullName());

        // 2. Lấy product qua Eureka (nếu không có instance → 503)
        ProductResponseDTO product = serviceClient.getProductById(request.getProductId());

        // 3. Kiểm tra stock
        if (product.getStockQuantity() < request.getQuantity()) {
            throw new RuntimeException("Insufficient stock. Available: " + product.getStockQuantity());
        }

        // 4. Tính totalAmount
        BigDecimal totalAmount = product.getPrice().multiply(
                BigDecimal.valueOf(request.getQuantity())
        );

        // 5. Tạo order
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .productPrice(product.getPrice())
                .totalAmount(totalAmount)
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order saved successfully with ID: {}", savedOrder.getId());

        OrderResponseDTO response = mapToResponseDTO(savedOrder);
        response.setCustomer(customer);
        response.setProduct(product);
        return response;
    }

    public OrderResponseDTO getOrderById(Long id) {
        log.info("Fetching order with ID: {}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));

        OrderResponseDTO response = mapToResponseDTO(order);

        // Lấy thêm thông tin customer và product
        try {
            CustomerResponseDTO customer = serviceClient.getCustomerById(order.getCustomerId());
            ProductResponseDTO product = serviceClient.getProductById(order.getProductId());
            response.setCustomer(customer);
            response.setProduct(product);
        } catch (Exception e) {
            log.warn("Could not fetch customer/product details: {}", e.getMessage());
        }

        return response;
    }

    public List<OrderResponseDTO> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<OrderResponseDTO> getOrdersByCustomer(Long customerId) {
        List<Order> orders = orderRepository.findByCustomerId(customerId);
        return orders.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponseDTO updateOrderStatus(Long id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + id));

        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        return mapToResponseDTO(updatedOrder);
    }

    private OrderResponseDTO mapToResponseDTO(Order order) {
        return OrderResponseDTO.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .productPrice(order.getProductPrice())
                .totalAmount(order.getTotalAmount())
                .orderDate(order.getOrderDate())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}