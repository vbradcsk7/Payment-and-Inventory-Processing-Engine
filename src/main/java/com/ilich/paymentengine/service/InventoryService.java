package com.ilich.paymentengine.service;

import com.ilich.paymentengine.dto.request.CreateProductRequest;
import com.ilich.paymentengine.dto.response.ProductResponse;
import com.ilich.paymentengine.exception.ProductNotFoundException;
import com.ilich.paymentengine.model.Product;
import com.ilich.paymentengine.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Obtains a pessimistic write lock on the product and reserves the requested stock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Product reserveStock(Long productId, int quantity) {
        log.debug("Acquiring pessimistic lock for product ID {} to reserve {} units", productId, quantity);
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.reserveStock(quantity);
        return productRepository.save(product);
    }

    /**
     * Commits reserved stock permanently when payment succeeds.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commitReservedStock(Long productId, int quantity) {
        log.debug("Acquiring pessimistic lock for product ID {} to commit {} reserved units", productId, quantity);
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.commitReservedStock(quantity);
        productRepository.save(product);
    }

    /**
     * Releases reserved stock back to available stock on payment cancellation or order expiration.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservedStock(Long productId, int quantity) {
        log.debug("Acquiring pessimistic lock for product ID {} to release {} units back to available", productId, quantity);
        Product product = productRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.releaseReservedStock(quantity);
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::fromEntity)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product(
                request.sku(),
                request.name(),
                request.stockAvailable(),
                request.price()
        );
        Product saved = productRepository.save(product);
        return ProductResponse.fromEntity(saved);
    }
}
