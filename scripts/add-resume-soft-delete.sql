-- Resume soft delete support (manual migration for prod ddl-auto: validate)

ALTER TABLE resumes ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_resumes_member_deleted_at ON resumes(member_id, deleted_at);
