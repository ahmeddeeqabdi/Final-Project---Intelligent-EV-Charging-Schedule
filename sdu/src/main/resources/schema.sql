ALTER TABLE IF EXISTS user_constraints
    ADD COLUMN IF NOT EXISTS price_area VARCHAR(3);

UPDATE user_constraints
SET price_area = 'DK2'
WHERE price_area IS NULL;

ALTER TABLE IF EXISTS user_constraints
    ALTER COLUMN price_area SET NOT NULL;