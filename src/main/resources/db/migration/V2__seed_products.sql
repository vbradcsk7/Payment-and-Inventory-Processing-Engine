-- ==============================================================================
-- V2__seed_products.sql
-- Seed products for demo and concurrency stress testing
-- ==============================================================================

INSERT INTO products (sku, name, stock_available, stock_reserved, price, version)
VALUES
    ('PROD-IPHONE-15-PRO', 'iPhone 15 Pro 128GB Titanium (Limited Drop)', 1, 0, 999.99, 0),
    ('PROD-MACBOOK-AIR-M3', 'MacBook Air 15" M3 16GB 512GB', 25, 0, 1499.00, 0),
    ('PROD-SONY-WH1000XM5', 'Sony WH-1000XM5 Wireless Noise Cancelling Headphones', 50, 0, 348.00, 0),
    ('PROD-LOGI-MX-MASTER-3S', 'Logitech MX Master 3S Wireless Mouse', 100, 0, 99.99, 0);
