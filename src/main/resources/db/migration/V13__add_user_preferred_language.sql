ALTER TABLE users
    ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'EN'
        CHECK (preferred_language IN ('EN', 'HI'));
