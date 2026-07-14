-- Login must resolve which tenant an email belongs to before TenantContext (and therefore
-- app.current_school_id) can be set — forced RLS on `users` otherwise hides every row during
-- that one bootstrap lookup, since there is no tenant to filter by yet. This adds a second,
-- narrowly-scoped SELECT policy that only opens when UserDetailsServiceImpl explicitly sets
-- app.pre_auth_lookup = 'true' around its single hardcoded lookup statement, resetting it
-- immediately after. Permissive policies for the same command are OR'd together by Postgres,
-- so this is strictly additive to the existing tenant_isolation policy.
CREATE POLICY pre_auth_login_lookup ON users
    FOR SELECT
    USING (current_setting('app.pre_auth_lookup', true) = 'true');
