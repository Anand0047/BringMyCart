ALTER TABLE saved_products
    ADD COLUMN last_manual_refresh_requested_at TIMESTAMPTZ;

CREATE INDEX idx_saved_products_manual_refresh
    ON saved_products (user_id, last_manual_refresh_requested_at);
