-- Group interview support (manual migration for prod ddl-auto: validate)

ALTER TABLE interviews ADD COLUMN IF NOT EXISTS max_participants INTEGER NOT NULL DEFAULT 1;
ALTER TABLE interviews ADD COLUMN IF NOT EXISTS mode VARCHAR(20) NOT NULL DEFAULT 'SOLO';
ALTER TABLE interviews ADD COLUMN IF NOT EXISTS current_speaker_member_id BIGINT;

ALTER TABLE interview_qnas ADD COLUMN IF NOT EXISTS respondent_member_id BIGINT;

CREATE TABLE IF NOT EXISTS interview_participants (
    id BIGSERIAL PRIMARY KEY,
    interview_id BIGINT NOT NULL REFERENCES interviews(id),
    member_id BIGINT NOT NULL REFERENCES members(id),
    role VARCHAR(20) NOT NULL,
    ready BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMP NOT NULL,
    resume_id BIGINT REFERENCES resumes(id),
    total_feedback TEXT,
    overall_score VARCHAR(20),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_interview_participant UNIQUE (interview_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_interview_participants_interview ON interview_participants(interview_id);
CREATE INDEX IF NOT EXISTS idx_interview_participants_member ON interview_participants(member_id);
