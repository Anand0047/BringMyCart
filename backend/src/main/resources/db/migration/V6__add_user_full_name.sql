ALTER TABLE users
    ADD COLUMN full_name VARCHAR(100) NULL;

ALTER TABLE users
    ADD CONSTRAINT ck_users_full_name_nonblank
        CHECK (full_name IS NULL OR length(trim(full_name)) > 0);
