ALTER TABLE series
    ADD COLUMN public_number BIGINT;

UPDATE series
SET public_number = COALESCE((regexp_match(name, '([0-9]+)[^0-9]*$'))[1]::bigint, 1);

ALTER TABLE series
    ALTER COLUMN public_number SET NOT NULL;
