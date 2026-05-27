ALTER TABLE urls ALTER COLUMN short_code DROP NOT NULL;
DROP INDEX idx_urls_short_code;
CREATE UNIQUE INDEX idx_urls_short_code ON urls (short_code) WHERE short_code IS NOT NULL;
