ALTER TABLE saved_products
    ADD COLUMN variant_key VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN variant_label VARCHAR(255);

ALTER TABLE saved_products
    DROP CONSTRAINT uk_saved_products_user_retailer_product,
    DROP CONSTRAINT ck_saved_products_retailer,
    ADD CONSTRAINT ck_saved_products_retailer
        CHECK (retailer IN ('AMAZON', 'FLIPKART', 'MYNTRA', 'MEESHO', 'TATA_NEU', 'AJIO', 'NYKAA_FASHION', 'NYKAA', 'CROMA')),
    ADD CONSTRAINT ck_saved_products_variant_key
        CHECK (length(variant_key) <= 100),
    ADD CONSTRAINT uk_saved_products_user_retailer_product_variant
        UNIQUE (user_id, retailer, retailer_product_id, variant_key);
