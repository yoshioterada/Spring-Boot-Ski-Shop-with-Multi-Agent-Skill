CREATE TABLE mail_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    correlation_id VARCHAR(100),
    recipient_email VARCHAR(255) NOT NULL,
    recipient_name VARCHAR(200),
    template_name VARCHAR(100) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL,
    azure_operation_id VARCHAR(200),
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMP WITH TIME ZONE,
    variables_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_mail_logs_status CHECK (status IN ('PENDING','SENDING','SENT','FAILED','SKIPPED'))
);

CREATE TABLE mail_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mail_log_id UUID NOT NULL REFERENCES mail_logs(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    content_id VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mail_logs_event_id ON mail_logs(event_id);
CREATE INDEX idx_mail_logs_status ON mail_logs(status);
CREATE INDEX idx_mail_logs_recipient ON mail_logs(recipient_email);
CREATE INDEX idx_mail_logs_created_at ON mail_logs(created_at);

CREATE INDEX idx_mail_attachments_mail_log_id ON mail_attachments(mail_log_id);

-- Trigger for automatic updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_mail_logs_updated_at
    BEFORE UPDATE ON mail_logs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_mail_attachments_updated_at
    BEFORE UPDATE ON mail_attachments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
