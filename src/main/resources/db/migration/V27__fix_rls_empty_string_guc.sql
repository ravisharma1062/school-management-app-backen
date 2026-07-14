-- V17's tenant_isolation policies cast current_setting('app.current_school_id', true) straight to
-- uuid, assuming an unset variable reads back as NULL. That's only true the FIRST time a session
-- ever references this custom GUC. Once any transaction on a pooled connection has set it (even
-- transaction-locally via set_config(..., true)), a later transaction on that SAME reused
-- connection that never re-sets it reads back an EMPTY STRING, not NULL — and ''::uuid throws
-- "invalid input syntax for type uuid", crashing the whole query instead of safely denying access.
-- Confirmed live: this broke UserDetailsServiceImpl's pre-auth lookup on a connection HikariCP had
-- previously used for a real tenant's authenticated request. NULLIF(..., '') closes the gap.
ALTER POLICY tenant_isolation ON users USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON teachers USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON students USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON attendance USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON timetable USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON homework USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON homework_submissions USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON exam_results USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON notices USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON fees USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON payments USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON leave_requests USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON books USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON book_issues USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON conversations USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON messages USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON events USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON event_rsvps USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON bus_routes USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON bus_stops USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON student_transport USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON notification_log USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON notification_preferences USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
ALTER POLICY tenant_isolation ON payment_claims USING (school_id = NULLIF(current_setting('app.current_school_id', true), '')::uuid);
