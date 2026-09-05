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

        try {
            // 1. Kiểm tra customer tồn tại
            CustomerResponseDTO customer = serviceClient.getCustomerById(request.getCustomerId());
            log.info("Customer found: {}", customer.getFullName());

            // 2. Lấy thông tin product (CÓ FALLBACK)
            ProductResponseDTO product = serviceClient.getProductById(request.getProductId());

            // Kiểm tra nếu product là fallback (giá = 0, stock = 0)
            boolean isFallback = product.getPrice().compareTo(BigDecimal.ZERO) == 0
                    && product.getStockQuantity() == 0;

            if (isFallback) {
                log.warn("Using fallback product data for order creation");
                // Vẫn cho phép tạo order nhưng với price = 0
            }

            // 3. Kiểm tra stock (chỉ kiểm tra nếu không phải fallback)
            if (!isFallback && product.getStockQuantity() < request.getQuantity()) {
                throw new RuntimeException("Insufficient stock. Available: " + product.getStockQuantity());
            }

            // 4. Tính toán totalAmount
            BigDecimal totalAmount = product.getPrice().multiply(
                    BigDecimal.valueOf(request.getQuantity())
            );

            // 5. Tạo order entity
            Order order = Order.builder()
                    .customerId(request.getCustomerId())
                    .productId(request.getProductId())
                    .quantity(request.getQuantity())
                    .productPrice(product.getPrice())
                    .totalAmount(totalAmount)
                    .orderDate(LocalDateTime.now())
                    .status(isFallback ? OrderStatus.PENDING_REVIEW : OrderStatus.PENDING)
                    .build();

            // 6. Lưu vào database
            Order savedOrder = orderRepository.save(order);
            log.info("Order saved successfully with ID: {}", savedOrder.getId());

            // 7. Trả về response
            OrderResponseDTO response = mapToResponseDTO(savedOrder);
            response.setCustomer(customer);
            response.setProduct(product);

            // Nếu là fallback, thêm warning message
            if (isFallback) {
                response.setMessage("Order created with fallback product data. Price may not be accurate.");
            }

            return response;

        } catch (Exception e) {
            log.error("Failed to create order: {}", e.getMessage());
            throw new RuntimeException("Failed to create order: " + e.getMessage());
        }
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