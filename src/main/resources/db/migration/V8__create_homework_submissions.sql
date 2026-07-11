CREATE TABLE homework_submissions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    homework_id      UUID NOT NULL REFERENCES homework (id),
    student_id       UUID NOT NULL REFERENCES students (id),
    file_key         VARCHAR(500) NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('SUBMITTED', 'GRADED')),
    teacher_feedback TEXT,
    grade            VARCHAR(10),
    submitted_at     TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_homework_submission_once UNIQUE (homework_id, student_id)
);

CREATE INDEX idx_homework_submissions_homework ON homework_submissions (homework_id);
CREATE INDEX idx_homework_submissions_student ON homework_submissions (student_id);
