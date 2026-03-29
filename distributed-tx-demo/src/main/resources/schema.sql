DROP TABLE IF EXISTS consumer_record;
DROP TABLE IF EXISTS outbox_event;
DROP TABLE IF EXISTS demo_order;
DROP TABLE IF EXISTS payment_instruction;
DROP TABLE IF EXISTS tcc_reservation;
DROP TABLE IF EXISTS wallet_account;

CREATE TABLE wallet_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_no VARCHAR(32) NOT NULL UNIQUE,
    balance DECIMAL(18, 2) NOT NULL,
    frozen_amount DECIMAL(18, 2) NOT NULL,
    version BIGINT NOT NULL
);

CREATE TABLE payment_instruction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    version BIGINT NOT NULL,
    last_message VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE demo_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE outbox_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_key VARCHAR(128) NOT NULL UNIQUE,
    topic VARCHAR(64) NOT NULL,
    payload VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE consumer_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    consumer_name VARCHAR(64) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_consumer_event UNIQUE (consumer_name, event_key)
);

CREATE TABLE tcc_reservation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    biz_no VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
