-- Ensure tag names are unique so we can lookup-or-create on insert.
-- Drop duplicate rows first (keep the lowest id per name).
DELETE t1 FROM tag t1
INNER JOIN tag t2
    ON t1.name = t2.name
   AND t1.id  > t2.id;

ALTER TABLE tag ADD CONSTRAINT uk_tag_name UNIQUE (name);
