-- Phase MT-1 RLS hardening (defense-in-depth): even a bug that bypasses the Hibernate @TenantId
-- filter — a hand-written native query, a future raw JDBC call, a misconfigured resolver — still
-- cannot read or write another school's rows, enforced by Postgres itself.
--
-- FORCE (not just ENABLE) is required: Postgres exempts a table's OWNING role from RLS by
-- default, and the app's DB role owns these tables (it created them via Flyway). Without FORCE,
-- the app's own connection would silently bypass every policy below.
--
-- The policy reads app.current_school_id, a per-transaction session variable set by
-- TenantRlsTransactionListener at the start of every transaction from TenantContext. The
-- `true` (missing_ok) argument to current_setting() means an unset variable compares to NULL
-- rather than erroring — NULL never equals any school_id, so the fail-safe default is zero rows,
-- not every row.

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE teachers FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON teachers USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE students ENABLE ROW LEVEL SECURITY;
ALTER TABLE students FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON students USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON attendance USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE timetable ENABLE ROW LEVEL SECURITY;
ALTER TABLE timetable FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON timetable USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE homework ENABLE ROW LEVEL SECURITY;
ALTER TABLE homework FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON homework USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE homework_submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE homework_submissions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON homework_submissions USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE exam_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE exam_results FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON exam_results USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE notices ENABLE ROW LEVEL SECURITY;
ALTER TABLE notices FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notices USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE fees ENABLE ROW LEVEL SECURITY;
ALTER TABLE fees FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON fees USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE leave_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE leave_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON leave_requests USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE books ENABLE ROW LEVEL SECURITY;
ALTER TABLE books FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON books USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE book_issues ENABLE ROW LEVEL SECURITY;
ALTER TABLE book_issues FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON book_issues USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON conversations USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON messages USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE events ENABLE ROW LEVEL SECURITY;
ALTER TABLE events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON events USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE event_rsvps ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_rsvps FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON event_rsvps USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE bus_routes ENABLE ROW LEVEL SECURITY;
ALTER TABLE bus_routes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON bus_routes USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE bus_stops ENABLE ROW LEVEL SECURITY;
ALTER TABLE bus_stops FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON bus_stops USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE student_transport ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_transport FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON student_transport USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE notification_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_log USING (school_id = current_setting('app.current_school_id', true)::uuid);

ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_preferences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_preferences USING (school_id = current_setting('app.current_school_id', true)::uuid);
