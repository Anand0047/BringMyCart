CREATE TABLE extension_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    client_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_extension_sessions_user ON extension_sessions (user_id, created_at DESC);
CREATE INDEX idx_extension_sessions_active ON extension_sessions (id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE extension_authorization_codes (
    id UUID PRIMARY KEY,
    code_hash CHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users (id),
    client_id VARCHAR(100) NOT NULL,
    redirect_uri VARCHAR(500) NOT NULL,
    state_hash CHAR(64) NOT NULL,
    code_challenge CHAR(43) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_extension_auth_codes_expiry ON extension_authorization_codes (expires_at);

CREATE TABLE extension_refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES extension_sessions (id),
    family_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    replaced_by_token_id UUID NULL REFERENCES extension_refresh_tokens (id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_extension_refresh_tokens_session ON extension_refresh_tokens (session_id);
CREATE INDEX idx_extension_refresh_tokens_family ON extension_refresh_tokens (family_id);
