-- Phase MT-6e: billing-owner vs. operational-admin split.

ALTER TABLE users ADD COLUMN is_billing_owner BOOLEAN NOT NULL DEFAULT false;

-- Every existing school needs exactly one billing owner from day one, so reassignment (which
-- requires an existing billing owner to authorize it) never locks a school out. Pick the
-- earliest-created ADMIN per school as a sensible default; schools can reassign afterward.
UPDATE users u
SET is_billing_owner = true
WHERE u.role = 'ADMIN'
  AND u.created_at = (
    SELECT MIN(u2.created_at) FROM users u2
    WHERE u2.school_id = u.school_id AND u2.role = 'ADMIN'
  );
