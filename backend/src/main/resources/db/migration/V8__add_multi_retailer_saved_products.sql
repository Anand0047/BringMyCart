ALTER TABLE saved_products
    ALTER COLUMN asin DROP NOT NULL,
    ALTER COLUMN currency DROP NOT NULL,
    ADD COLUMN retailer VARCHAR(16) NOT NULL DEFAULT 'AMAZON',
    ADD COLUMN retailer_product_id VARCHAR(80),
    ADD COLUMN metadata_source VARCHAR(24) NOT NULL DEFAULT 'URL_ONLY',
    ADD COLUMN product_description TEXT,
    ADD COLUMN product_images JSONB,
    ADD COLUMN product_features JSONB,
    ADD COLUMN product_categories JSONB,
    ADD COLUMN product_specifications JSONB,
    ADD COLUMN manufacturer VARCHAR(255);

UPDATE saved_products
SET retailer_product_id = asin,
    metadata_source = CASE WHEN metadata_fetched_at IS NOT NULL THEN 'AXESSO' ELSE 'URL_ONLY' END;

ALTER TABLE saved_products
    ALTER COLUMN retailer_product_id SET NOT NULL,
    ADD CONSTRAINT uk_saved_products_user_retailer_product
        UNIQUE (user_id, retailer, retailer_product_id),
    ADD CONSTRAINT ck_saved_products_retailer
        CHECK (retailer IN ('AMAZON', 'FLIPKART', 'MYNTRA')),
    ADD CONSTRAINT ck_saved_products_retailer_product_id
        CHECK (length(retailer_product_id) BETWEEN 5 AND 80),
    ADD CONSTRAINT ck_saved_products_metadata_source
        CHECK (metadata_source IN ('URL_ONLY', 'EXTENSION', 'SCRAPEDO', 'AXESSO'));

CREATE INDEX idx_saved_products_user_retailer_created_at
    ON saved_products (user_id, retailer, created_at DESC, id DESC);
