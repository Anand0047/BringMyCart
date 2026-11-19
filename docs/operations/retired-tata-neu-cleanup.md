# Retired Tata Neu record cleanup (manual only)

Tata Neu support is retired. Existing rows are retained and returned as `UNSUPPORTED`; the application does not delete them. Do not run cleanup as part of startup, migration, deployment, or normal product operations.

If a data owner later explicitly authorizes removal, first take a verified database backup and inspect affected records. Run the preview query in a read-only session:

```sql
SELECT id, user_id, retailer_product_id, original_url, created_at
FROM saved_products
WHERE retailer = 'TATA_NEU'
ORDER BY created_at, id;
```

After reviewing the exact rows and confirming retention requirements, an operator may execute a separately reviewed transaction. The following is intentionally guidance only; it is not a migration and is not run by the application:

```sql
BEGIN;
DELETE FROM saved_products
WHERE retailer = 'TATA_NEU';
-- Verify the affected row count and transaction contents before COMMIT.
-- ROLLBACK if the reviewed scope is unexpected.
COMMIT;
```

Deleting the rows also removes their saved-product metadata. This operation is irreversible without a backup. Do not remove `TATA_NEU` from the already-applied V9 constraint as part of retirement; new URL validation already rejects new saves, while retaining the constraint keeps historical records compatible until an authorized cleanup has actually removed them.
