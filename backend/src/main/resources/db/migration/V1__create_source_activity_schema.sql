-- Owns the immutable source-shaped customer/activity/risk facts; later analysis remains advisory.
CREATE TYPE activity_type_enum AS ENUM ('CARD', 'PAYMENT', 'CRYPTO');
CREATE TYPE risk_applies_to_enum AS ENUM ('CARD', 'PAYMENT', 'CRYPTO', 'ALL');
CREATE TABLE customers (
    customer_id UUID PRIMARY KEY
);
CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(customer_id),
    activity_type activity_type_enum NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE TABLE card_activity (
    transaction_id UUID PRIMARY KEY REFERENCES transactions(transaction_id),
    card_pan VARCHAR NOT NULL,
    card_type VARCHAR NOT NULL,
    merchant_name VARCHAR NOT NULL,
    mcc_code VARCHAR(4) NOT NULL,
    card_present BOOLEAN NOT NULL,
    authorization_code VARCHAR NOT NULL,
    decline_reason VARCHAR
);
CREATE TABLE payment_activity (
    transaction_id UUID PRIMARY KEY REFERENCES transactions(transaction_id),
    payment_method VARCHAR NOT NULL,
    sender_account VARCHAR NOT NULL,
    receiver_account VARCHAR NOT NULL,
    receiver_bank_country CHAR(2) NOT NULL
);
CREATE TABLE crypto_activity (
    transaction_id UUID PRIMARY KEY REFERENCES transactions(transaction_id),
    blockchain VARCHAR NOT NULL,
    wallet_address_from VARCHAR NOT NULL,
    wallet_address_to VARCHAR NOT NULL,
    tx_hash VARCHAR NOT NULL,
    exchange_name VARCHAR
);
CREATE TABLE risk_rules (
    rule_id UUID PRIMARY KEY,
    rule_name VARCHAR NOT NULL,
    applies_to risk_applies_to_enum NOT NULL,
    threshold_logic TEXT NOT NULL,
    weight DECIMAL(5,2) NOT NULL
);
CREATE TABLE risk_assessments (
    assessment_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(transaction_id),
    rule_id UUID NOT NULL REFERENCES risk_rules(rule_id),
    triggered_at TIMESTAMP NOT NULL,
    score_contribution DECIMAL(5,2) NOT NULL
);
CREATE INDEX transactions_customer_created_idx ON transactions(customer_id, created_at DESC);
CREATE INDEX risk_assessments_transaction_idx ON risk_assessments(transaction_id);
