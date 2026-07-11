ALTER TABLE students ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE notices ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE timetable ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX idx_students_is_active ON students (is_active);
CREATE INDEX idx_notices_is_active ON notices (is_active);
CREATE INDEX idx_timetable_is_active ON timetable (is_active);
