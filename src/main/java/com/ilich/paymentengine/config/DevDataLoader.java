package com.ilich.paymentengine.config;

import com.ilich.paymentengine.model.Product;
import com.ilich.paymentengine.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Profile("local")
public class DevDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);

    private final ProductRepository productRepository;

    public DevDataLoader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            log.info("Running in 'local' in-memory mode. Seeding initial demo products...");
            List<Product> seedProducts = List.of(
                    new Product("PROD-IPHONE-15-PRO", "iPhone 15 Pro 128GB Titanium (Limited Drop)", 1, BigDecimal.valueOf(999.99)),
                    new Product("PROD-MACBOOK-AIR-M3", "MacBook Air 15\" M3 16GB 512GB", 25, BigDecimal.valueOf(1499.00)),
                    new Product("PROD-SONY-WH1000XM5", "Sony WH-1000XM5 Wireless Noise Cancelling Headphones", 50, BigDecimal.valueOf(348.00)),
                    new Product("PROD-LOGI-MX-MASTER-3S", "Logitech MX Master 3S Wireless Mouse", 100, BigDecimal.valueOf(99.99))
            );
            productRepository.saveAll(seedProducts);
            log.info("Seeded {} products for local development & testing.", seedProducts.size());
        }
    }
}
