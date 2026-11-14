CREATE TABLE app_users (
 id UUID PRIMARY KEY, username VARCHAR(64) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL,
 CONSTRAINT user_role CHECK (role IN ('PARTICIPANT','ADMIN','FINANCE'))
);
CREATE TABLE workshop_registrations (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES app_users(id),
 attendee_name VARCHAR(100) NOT NULL, email VARCHAR(254) NOT NULL,
 mobile VARCHAR(13) NOT NULL, organisation VARCHAR(150) NOT NULL,
 amount_due NUMERIC(10,2) NOT NULL, currency VARCHAR(3) NOT NULL,
 payment_status VARCHAR(20) NOT NULL, version BIGINT NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 deleted_at TIMESTAMP WITH TIME ZONE,
 CONSTRAINT fixed_workshop_fee CHECK (amount_due=2500.00 AND currency='INR'),
 CONSTRAINT registration_status CHECK (payment_status IN ('UNPAID','PENDING','PAID')),
 CONSTRAINT registration_version CHECK (version>=0),
 CONSTRAINT deletion_status CHECK (deleted_at IS NULL OR payment_status='UNPAID')
);
CREATE INDEX registration_owner_idx ON workshop_registrations(owner_id,deleted_at);
CREATE TABLE payment_attempts (
 id UUID PRIMARY KEY, registration_id UUID NOT NULL REFERENCES workshop_registrations(id),
 order_reference VARCHAR(40) NOT NULL UNIQUE, amount NUMERIC(10,2) NOT NULL,
 currency VARCHAR(3) NOT NULL, status VARCHAR(20) NOT NULL,
 receipt VARCHAR(100) UNIQUE, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 resolved_at TIMESTAMP WITH TIME ZONE,
 CONSTRAINT payment_fee CHECK (amount=2500.00 AND currency='INR'),
 CONSTRAINT attempt_status CHECK (status IN ('PENDING','SETTLED','FAILED')),
 CONSTRAINT payment_resolution CHECK ((status='PENDING' AND receipt IS NULL AND resolved_at IS NULL)
   OR (status IN ('SETTLED','FAILED') AND receipt IS NOT NULL AND resolved_at IS NOT NULL))
);
CREATE INDEX payment_registration_idx ON payment_attempts(registration_id);
CREATE TABLE audit_events (
 id UUID PRIMARY KEY, registration_id UUID NOT NULL REFERENCES workshop_registrations(id),
 actor VARCHAR(64) NOT NULL, action VARCHAR(40) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
