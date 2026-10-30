CREATE TABLE bank_user (
  username VARCHAR(80) PRIMARY KEY,
  display_name VARCHAR(120) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','STAFF'))
);
CREATE TABLE bank_account (
  id VARCHAR(80) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  masked_number VARCHAR(30) NOT NULL,
  currency CHAR(3) NOT NULL,
  balance DECIMAL(19,2) NOT NULL
);
CREATE TABLE account_access (
  username VARCHAR(80) NOT NULL REFERENCES bank_user(username),
  account_id VARCHAR(80) NOT NULL REFERENCES bank_account(id),
  PRIMARY KEY (username,account_id)
);
-- This immutable projection is populated exclusively by a trusted ledger adapter.
-- Dispute code never updates transaction values. A reversal is a linked ledger event.
CREATE TABLE ledger_transaction (
  id VARCHAR(80) PRIMARY KEY,
  account_id VARCHAR(80) NOT NULL REFERENCES bank_account(id),
  provider_reference VARCHAR(100) NOT NULL UNIQUE,
  merchant VARCHAR(180) NOT NULL,
  amount DECIMAL(19,2) NOT NULL,
  currency CHAR(3) NOT NULL,
  booked_at TIMESTAMP WITH TIME ZONE NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('BOOKED','PENDING','REVERSED')),
  category VARCHAR(60) NOT NULL,
  observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  reversal_of VARCHAR(80) REFERENCES ledger_transaction(id)
);
CREATE INDEX idx_transaction_account_date ON ledger_transaction(account_id,booked_at DESC);
CREATE TABLE dispute_case (
  id VARCHAR(80) PRIMARY KEY,
  case_reference VARCHAR(50) NOT NULL UNIQUE,
  transaction_id VARCHAR(80) NOT NULL REFERENCES ledger_transaction(id),
  submitted_by VARCHAR(80) NOT NULL REFERENCES bank_user(username),
  reason VARCHAR(40) NOT NULL CHECK (reason IN ('UNRECOGNIZED','DUPLICATE','INCORRECT_AMOUNT','GOODS_NOT_RECEIVED')),
  description VARCHAR(2000) NOT NULL,
  status VARCHAR(30) NOT NULL CHECK (status IN ('SUBMITTED','UNDER_REVIEW','AWAITING_INFORMATION','RESOLVED')),
  active_transaction_id VARCHAR(80) UNIQUE REFERENCES ledger_transaction(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  resolution VARCHAR(2000),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT active_matches_status CHECK ((status='RESOLVED' AND active_transaction_id IS NULL) OR (status<>'RESOLVED' AND active_transaction_id IS NOT NULL AND active_transaction_id=transaction_id))
);
CREATE INDEX idx_dispute_transaction ON dispute_case(transaction_id,created_at DESC);
CREATE TABLE dispute_history (
  event_sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  id VARCHAR(80) PRIMARY KEY,
  dispute_id VARCHAR(80) NOT NULL REFERENCES dispute_case(id),
  from_status VARCHAR(30),
  to_status VARCHAR(30) NOT NULL,
  actor VARCHAR(80) NOT NULL REFERENCES bank_user(username),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  note VARCHAR(2000) NOT NULL
);
CREATE INDEX idx_history_case_time ON dispute_history(dispute_id,occurred_at);
CREATE TABLE audit_event (
  id VARCHAR(80) PRIMARY KEY,
  actor VARCHAR(80) NOT NULL,
  action VARCHAR(80) NOT NULL,
  entity_id VARCHAR(80),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  change_summary VARCHAR(1000) NOT NULL
);
CREATE INDEX idx_audit_time ON audit_event(occurred_at);
CREATE TABLE idempotency_record (
  username VARCHAR(80) NOT NULL REFERENCES bank_user(username),
  request_key VARCHAR(100) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  dispute_id VARCHAR(80) NOT NULL REFERENCES dispute_case(id),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY(username,request_key)
);


