package com.example.orderservice.client;

import com.example.orderservice.dto.CustomerResponseDTO;
import com.example.orderservice.dto.ProductResponseDTO;
import com.example.orderservice.exception.ProductServiceUnavailableException;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.customer.url:http://localhost:8081}")
    private String customerServiceUrl;

    // Dùng tên service đăng ký trên Eureka (không hardcode host/port)
    private static final String PRODUCT_SERVICE_URL = "http://PRODUCT-SERVICE";

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

    /**
     * Gọi Product Service qua Load-Balanced RestTemplate.
     * URL dùng tên service: http://PRODUCT-SERVICE/...
     * Spring Cloud LoadBalancer sẽ tự chọn instance (round-robin) từ Eureka.
     */
    public ProductResponseDTO getProductById(Long id) {
        String url = PRODUCT_SERVICE_URL + "/api/v1/products/" + id;
        log.info("Calling Product Service (LoadBalanced): {}", url);

        try {
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
            throw new ProductServiceUnavailableException(
                    "Product Service is experiencing issues: " + e.getMessage(), e
            );

        } catch (ResourceAccessException e) {
            log.error("Cannot connect to PRODUCT-SERVICE: {}", e.getMessage());
            throw new ProductServiceUnavailableException(
                    "PRODUCT-SERVICE is not available. No instances or service is down.", e
            );

        } catch (ProductServiceUnavailableException e) {
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error calling PRODUCT-SERVICE: {}", e.getMessage());
            throw new ProductServiceUnavailableException(
                    "Unexpected error calling PRODUCT-SERVICE: " + e.getMessage(), e
            );
        }
    }
}