-- Existing extension sessions were not associated with an authoritative website session.
-- Revoke them before the new binding becomes mandatory for newly authorized sessions.
UPDATE extension_sessions
SET revoked_at = now()
WHERE revoked_at IS NULL;

ALTER TABLE extension_sessions
    ADD COLUMN website_family_id UUID;

ALTER TABLE extension_authorization_codes
    ADD COLUMN website_family_id UUID;

UPDATE extension_authorization_codes
SET consumed_at = now()
WHERE consumed_at IS NULL;

ALTER TABLE extension_sessions
    ADD CONSTRAINT ck_extension_sessions_active_website_binding
    CHECK (revoked_at IS NOT NULL OR website_family_id IS NOT NULL);

ALTER TABLE extension_authorization_codes
    ADD CONSTRAINT ck_extension_auth_codes_unconsumed_website_binding
    CHECK (consumed_at IS NOT NULL OR website_family_id IS NOT NULL);

CREATE INDEX idx_extension_sessions_website_family
    ON extension_sessions (user_id, website_family_id)
    WHERE revoked_at IS NULL;

CREATE INDEX idx_extension_auth_codes_website_family
    ON extension_authorization_codes (user_id, website_family_id, expires_at)
    WHERE consumed_at IS NULL;
