CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'TEACHER', 'PARENT')),
    phone         VARCHAR(20),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE students (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    roll_no    VARCHAR(50) NOT NULL,
    class      VARCHAR(20) NOT NULL,
    section    VARCHAR(10) NOT NULL,
    dob        DATE NOT NULL,
    parent_id  UUID REFERENCES users (id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_students_class_section_roll UNIQUE (class, section, roll_no)
);

CREATE TABLE teachers (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL UNIQUE REFERENCES users (id),
    subjects         VARCHAR(500),
    classes_assigned VARCHAR(500)
);

CREATE TABLE attendance (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students (id),
    date       DATE NOT NULL,
    status     VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    marked_by  UUID NOT NULL REFERENCES users (id),
    CONSTRAINT uq_attendance_student_date UNIQUE (student_id, date)
);

CREATE TABLE timetable (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class        VARCHAR(20) NOT NULL,
    section      VARCHAR(10) NOT NULL,
    day_of_week  VARCHAR(10) NOT NULL,
    period       INT NOT NULL,
    subject      VARCHAR(100) NOT NULL,
    teacher_id   UUID NOT NULL REFERENCES teachers (id),
    CONSTRAINT uq_timetable_slot UNIQUE (class, section, day_of_week, period)
);

CREATE TABLE homework (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class       VARCHAR(20) NOT NULL,
    section     VARCHAR(10) NOT NULL,
    subject     VARCHAR(100) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    due_date    DATE NOT NULL,
    created_by  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE exam_results (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id     UUID NOT NULL REFERENCES students (id),
    subject        VARCHAR(100) NOT NULL,
    exam_name      VARCHAR(100) NOT NULL,
    marks_obtained DECIMAL(6, 2) NOT NULL,
    max_marks      DECIMAL(6, 2) NOT NULL,
    grade          VARCHAR(5) NOT NULL,
    term           VARCHAR(50) NOT NULL
);

CREATE TABLE notices (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    target_role VARCHAR(20) NOT NULL CHECK (target_role IN ('ADMIN', 'TEACHER', 'PARENT', 'ALL')),
    created_by  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE fees (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID NOT NULL REFERENCES students (id),
    term         VARCHAR(50) NOT NULL,
    amount_due   DECIMAL(10, 2) NOT NULL,
    amount_paid  DECIMAL(10, 2) NOT NULL DEFAULT 0,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'OVERDUE')),
    due_date     DATE NOT NULL,
    CONSTRAINT uq_fees_student_term UNIQUE (student_id, term)
);

CREATE INDEX idx_students_parent_id ON students (parent_id);
CREATE INDEX idx_students_class_section ON students (class, section);
CREATE INDEX idx_attendance_student_id ON attendance (student_id);
CREATE INDEX idx_attendance_date ON attendance (date);
CREATE INDEX idx_timetable_class_section ON timetable (class, section);
CREATE INDEX idx_homework_class_section ON homework (class, section);
CREATE INDEX idx_exam_results_student_id ON exam_results (student_id);
CREATE INDEX idx_notices_target_role ON notices (target_role);
CREATE INDEX idx_fees_student_id ON fees (student_id);
