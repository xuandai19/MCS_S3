package com.example.orderservice.client;

import com.example.orderservice.dto.CustomerResponseDTO;
import com.example.orderservice.dto.ProductResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceClient {

    private final RestTemplate restTemplate;
    // Xóa ObjectMapper - không cần thiết

    @Value("${services.customer.url:http://localhost:8081}")
    private String customerServiceUrl;

    @Value("${services.product.url:http://localhost:8082}")
    private String productServiceUrl;

    // Customer Service - Không có fallback
    public CustomerResponseDTO getCustomerById(Long id) {
        try {
            String url = customerServiceUrl + "/api/v1/customers/" + id;
            log.info("Calling Customer Service: {}", url);

            ResponseEntity<CustomerResponseDTO> response = restTemplate.getForEntity(
                    url, CustomerResponseDTO.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            throw new RuntimeException("Invalid response from Customer Service");

        } catch (HttpClientErrorException.NotFound e) {
            log.error("Customer not found with ID: {}", id);
            throw new RuntimeException("Customer not found with ID: " + id);
        } catch (Exception e) {
            log.error("Error calling Customer Service: {}", e.getMessage());
            throw new RuntimeException("Customer Service unavailable");
        }
    }

    // Product Service - CÓ FALLBACK
    public ProductResponseDTO getProductById(Long id) {
        try {
            String url = productServiceUrl + "/api/v1/products/" + id;
            log.info("Calling Product Service: {}", url);

            ResponseEntity<ProductResponseDTO> response = restTemplate.getForEntity(
                    url, ProductResponseDTO.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Product found: {}, price: {}",
                        response.getBody().getName(),
                        response.getBody().getPrice()
                );
                return response.getBody();
            }

            throw new RuntimeException("Invalid response from Product Service");

        } catch (HttpClientErrorException.NotFound e) {
            log.error("Product not found with ID: {}", id);
            throw new RuntimeException("Product not found with ID: " + id);

        } catch (HttpServerErrorException e) {
            log.error("Product Service internal error: {}", e.getMessage());
            // Fallback: Trả về product mặc định
            return getFallbackProduct(id, "Product Service is experiencing issues");

        } catch (ResourceAccessException e) {
            log.error("Cannot connect to Product Service: {}", e.getMessage());
            // Fallback: Trả về product mặc định
            return getFallbackProduct(id, "Product Service is currently unavailable");

        } catch (Exception e) {
            log.error("Unexpected error when calling Product Service: {}", e.getMessage());
            // Fallback: Trả về product mặc định
            return getFallbackProduct(id, "Product Service error");
        }
    }

    // Fallback: Trả về product mặc định
    private ProductResponseDTO getFallbackProduct(Long id, String reason) {
        log.warn("Using fallback product for ID: {}, Reason: {}", id, reason);

        return ProductResponseDTO.builder()
                .id(id)
                .name("Product Temporarily Unavailable (Fallback)")
                .price(BigDecimal.ZERO)
                .stockQuantity(0)
                .build();
    }
}