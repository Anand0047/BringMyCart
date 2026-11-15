ALTER TABLE saved_products
    ADD COLUMN enrichment_error_category VARCHAR(50),
    ADD COLUMN enrichment_next_attempt_at TIMESTAMPTZ;

CREATE INDEX idx_saved_products_enrichment_recovery
    ON saved_products (enrichment_status, enrichment_next_attempt_at)
    WHERE enrichment_status IN ('PENDING', 'PROCESSING', 'FAILED');
