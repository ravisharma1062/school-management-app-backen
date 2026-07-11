CREATE TABLE books (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title             VARCHAR(255) NOT NULL,
    author            VARCHAR(255) NOT NULL,
    isbn              VARCHAR(32),
    total_copies      INT NOT NULL,
    available_copies  INT NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE book_issues (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id      UUID NOT NULL REFERENCES books (id),
    student_id   UUID NOT NULL REFERENCES students (id),
    issued_at    DATE NOT NULL,
    due_date     DATE NOT NULL,
    returned_at  DATE,
    fine_amount  DECIMAL(10, 2),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('ISSUED', 'RETURNED')),

    CONSTRAINT chk_book_issues_returned CHECK (status = 'ISSUED' OR returned_at IS NOT NULL)
);

CREATE INDEX idx_book_issues_student_id ON book_issues (student_id);
CREATE INDEX idx_book_issues_book_id ON book_issues (book_id);
