-- V25's two-statement app-side approach (SET flag, then SELECT, on the same JDBC connection)
-- assumed session state carries cleanly between those statements. In production that assumption
-- didn't hold — the app still couldn't resolve a login's tenant. Replacing it with a single
-- atomic function call removes any dependency on connection/transaction continuity between
-- separate JDBC statements: the flag is set, read, and reset entirely within one server-side
-- call, so it's correct regardless of what transaction (if any) the caller is already inside.
CREATE OR REPLACE FUNCTION resolve_login_school_id(p_email text)
RETURNS uuid
LANGUAGE plpgsql
AS $$
DECLARE
    v_school_id uuid;
BEGIN
    PERFORM set_config('app.pre_auth_lookup', 'true', true);
    SELECT school_id INTO v_school_id FROM users WHERE email = p_email;
    PERFORM set_config('app.pre_auth_lookup', 'false', true);
    RETURN v_school_id;
END;
$$;
