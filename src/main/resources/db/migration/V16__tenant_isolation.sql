-- Phase MT-1: convert the single-school app into a multi-tenant one. Every existing row belongs
-- to one school, so this migration creates that school and scopes all existing data to it.
--
-- Order matters and must stay in this shape on a live database:
--   1. create schools
--   2. add school_id (nullable) to every tenant-owned table
--   3. backfill school_id on every row to the one pre-existing school
--   4. set NOT NULL + FK + index once every row is populated
-- Adding NOT NULL before backfilling would fail immediately; skipping the nullable step first
-- and going straight to NOT NULL with a default would work but forces an unnecessary rewrite
-- lock window on tables that already have rows.

-- 1. Tenant root -------------------------------------------------------------------------------

CREATE TABLE schools (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 2. Add school_id (nullable for now) to every tenant-owned table ------------------------------

ALTER TABLE users                   ADD COLUMN school_id UUID;
ALTER TABLE teachers                ADD COLUMN school_id UUID;
ALTER TABLE students                ADD COLUMN school_id UUID;
ALTER TABLE attendance              ADD COLUMN school_id UUID;
ALTER TABLE timetable               ADD COLUMN school_id UUID;
ALTER TABLE homework                ADD COLUMN school_id UUID;
ALTER TABLE homework_submissions    ADD COLUMN school_id UUID;
ALTER TABLE exam_results            ADD COLUMN school_id UUID;
ALTER TABLE notices                 ADD COLUMN school_id UUID;
ALTER TABLE fees                    ADD COLUMN school_id UUID;
ALTER TABLE payments                ADD COLUMN school_id UUID;
ALTER TABLE leave_requests          ADD COLUMN school_id UUID;
ALTER TABLE books                   ADD COLUMN school_id UUID;
ALTER TABLE book_issues             ADD COLUMN school_id UUID;
ALTER TABLE conversations           ADD COLUMN school_id UUID;
ALTER TABLE messages                ADD COLUMN school_id UUID;
ALTER TABLE events                  ADD COLUMN school_id UUID;
ALTER TABLE event_rsvps             ADD COLUMN school_id UUID;
ALTER TABLE bus_routes              ADD COLUMN school_id UUID;
ALTER TABLE bus_stops               ADD COLUMN school_id UUID;
ALTER TABLE student_transport       ADD COLUMN school_id UUID;
ALTER TABLE notification_log        ADD COLUMN school_id UUID;
ALTER TABLE notification_preferences ADD COLUMN school_id UUID;

-- notification_preferences was one global row per event type (PK = event_type). Once every
-- school has its own row per event type, event_type alone can no longer be the primary key —
-- give the table a generated id and re-key on (school_id, event_type) further down.
ALTER TABLE notification_preferences ADD COLUMN id UUID DEFAULT gen_random_uuid();

-- 3. Create the one pre-existing school and backfill every row to it ---------------------------

DO $$
DECLARE
    v_school_id UUID;
BEGIN
    INSERT INTO schools (id, name, slug, status)
    VALUES (gen_random_uuid(), 'Default School', 'default-school', 'ACTIVE')
    RETURNING id INTO v_school_id;

    UPDATE users                    SET school_id = v_school_id;
    UPDATE teachers                 SET school_id = v_school_id;
    UPDATE students                 SET school_id = v_school_id;
    UPDATE attendance               SET school_id = v_school_id;
    UPDATE timetable                SET school_id = v_school_id;
    UPDATE homework                 SET school_id = v_school_id;
    UPDATE homework_submissions     SET school_id = v_school_id;
    UPDATE exam_results             SET school_id = v_school_id;
    UPDATE notices                  SET school_id = v_school_id;
    UPDATE fees                     SET school_id = v_school_id;
    UPDATE payments                 SET school_id = v_school_id;
    UPDATE leave_requests           SET school_id = v_school_id;
    UPDATE books                    SET school_id = v_school_id;
    UPDATE book_issues              SET school_id = v_school_id;
    UPDATE conversations            SET school_id = v_school_id;
    UPDATE messages                 SET school_id = v_school_id;
    UPDATE events                   SET school_id = v_school_id;
    UPDATE event_rsvps              SET school_id = v_school_id;
    UPDATE bus_routes               SET school_id = v_school_id;
    UPDATE bus_stops                SET school_id = v_school_id;
    UPDATE student_transport        SET school_id = v_school_id;
    UPDATE notification_log         SET school_id = v_school_id;
    UPDATE notification_preferences SET school_id = v_school_id;
END $$;

-- 4. Lock it down: NOT NULL + FK + index, now that every row is populated ----------------------

ALTER TABLE users                   ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE teachers                ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE students                ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE attendance              ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE timetable               ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE homework                ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE homework_submissions    ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE exam_results            ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE notices                 ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE fees                    ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE payments                ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE leave_requests          ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE books                   ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE book_issues             ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE conversations           ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE messages                ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE events                  ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE event_rsvps             ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE bus_routes              ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE bus_stops               ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE student_transport       ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE notification_log        ALTER COLUMN school_id SET NOT NULL;
ALTER TABLE notification_preferences ALTER COLUMN school_id SET NOT NULL;

ALTER TABLE users                   ADD CONSTRAINT fk_users_school                   FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE teachers                ADD CONSTRAINT fk_teachers_school                FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE students                ADD CONSTRAINT fk_students_school                FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE attendance              ADD CONSTRAINT fk_attendance_school              FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE timetable               ADD CONSTRAINT fk_timetable_school               FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE homework                ADD CONSTRAINT fk_homework_school                FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE homework_submissions    ADD CONSTRAINT fk_homework_submissions_school    FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE exam_results            ADD CONSTRAINT fk_exam_results_school            FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE notices                 ADD CONSTRAINT fk_notices_school                 FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE fees                    ADD CONSTRAINT fk_fees_school                    FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE payments                ADD CONSTRAINT fk_payments_school                FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE leave_requests          ADD CONSTRAINT fk_leave_requests_school          FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE books                   ADD CONSTRAINT fk_books_school                   FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE book_issues             ADD CONSTRAINT fk_book_issues_school             FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE conversations           ADD CONSTRAINT fk_conversations_school           FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE messages                ADD CONSTRAINT fk_messages_school                FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE events                  ADD CONSTRAINT fk_events_school                  FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE event_rsvps             ADD CONSTRAINT fk_event_rsvps_school             FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE bus_routes              ADD CONSTRAINT fk_bus_routes_school              FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE bus_stops               ADD CONSTRAINT fk_bus_stops_school              FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE student_transport       ADD CONSTRAINT fk_student_transport_school       FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE notification_log        ADD CONSTRAINT fk_notification_log_school        FOREIGN KEY (school_id) REFERENCES schools (id);
ALTER TABLE notification_preferences ADD CONSTRAINT fk_notification_preferences_school FOREIGN KEY (school_id) REFERENCES schools (id);

CREATE INDEX idx_users_school_id ON users (school_id);
CREATE INDEX idx_teachers_school_id ON teachers (school_id);
CREATE INDEX idx_students_school_id ON students (school_id);
CREATE INDEX idx_attendance_school_id ON attendance (school_id);
CREATE INDEX idx_timetable_school_id ON timetable (school_id);
CREATE INDEX idx_homework_school_id ON homework (school_id);
CREATE INDEX idx_homework_submissions_school_id ON homework_submissions (school_id);
CREATE INDEX idx_exam_results_school_id ON exam_results (school_id);
CREATE INDEX idx_notices_school_id ON notices (school_id);
CREATE INDEX idx_fees_school_id ON fees (school_id);
CREATE INDEX idx_payments_school_id ON payments (school_id);
CREATE INDEX idx_leave_requests_school_id ON leave_requests (school_id);
CREATE INDEX idx_books_school_id ON books (school_id);
CREATE INDEX idx_book_issues_school_id ON book_issues (school_id);
CREATE INDEX idx_conversations_school_id ON conversations (school_id);
CREATE INDEX idx_messages_school_id ON messages (school_id);
CREATE INDEX idx_events_school_id ON events (school_id);
CREATE INDEX idx_event_rsvps_school_id ON event_rsvps (school_id);
CREATE INDEX idx_bus_routes_school_id ON bus_routes (school_id);
CREATE INDEX idx_bus_stops_school_id ON bus_stops (school_id);
CREATE INDEX idx_student_transport_school_id ON student_transport (school_id);
CREATE INDEX idx_notification_log_school_id ON notification_log (school_id);
CREATE INDEX idx_notification_preferences_school_id ON notification_preferences (school_id);

-- Composite indexes for the hottest existing "by class/section" query patterns, now that
-- school_id is always part of the effective WHERE clause (added automatically by Hibernate's
-- @TenantId filter on every query).
CREATE INDEX idx_students_school_class_section ON students (school_id, class, section);
CREATE INDEX idx_timetable_school_class_section ON timetable (school_id, class, section);
CREATE INDEX idx_homework_school_class_section ON homework (school_id, class, section);

-- 5. Re-key notification_preferences: id becomes the PK, event_type becomes a plain column,
--    uniqueness moves to (school_id, event_type).
ALTER TABLE notification_preferences ALTER COLUMN id SET NOT NULL;
ALTER TABLE notification_preferences DROP CONSTRAINT notification_preferences_pkey;
ALTER TABLE notification_preferences ADD PRIMARY KEY (id);
ALTER TABLE notification_preferences ADD CONSTRAINT uq_notification_preferences_school_event UNIQUE (school_id, event_type);

-- 6. Widen existing UNIQUE constraints that are keyed on plain strings (class/section) rather
--    than a foreign key into another tenant-owned table. Constraints keyed by a FK (student_id,
--    event_id, route_id, ...) are already implicitly tenant-safe, since that referenced row can
--    only belong to one school. These two are not: without school_id, two different schools
--    could not both use "Class 7 Section C" or the same weekly timetable slot.
ALTER TABLE students DROP CONSTRAINT uq_students_class_section_roll;
ALTER TABLE students ADD CONSTRAINT uq_students_school_class_section_roll UNIQUE (school_id, class, section, roll_no);

ALTER TABLE timetable DROP CONSTRAINT uq_timetable_slot;
ALTER TABLE timetable ADD CONSTRAINT uq_timetable_school_slot UNIQUE (school_id, class, section, day_of_week, period);
