CREATE TABLE conversations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id  UUID NOT NULL REFERENCES users (id),
    teacher_id UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_conversation_participants UNIQUE (parent_id, teacher_id)
);

CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations (id),
    sender_id       UUID NOT NULL REFERENCES users (id),
    body            TEXT NOT NULL,
    sent_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_parent_id ON conversations (parent_id);
CREATE INDEX idx_conversations_teacher_id ON conversations (teacher_id);
CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
