CREATE TABLE subscriber_accounts (
    account_id  VARCHAR(20) NOT NULL,
    msisdn      VARCHAR(15) NOT NULL,
    plan_code   VARCHAR(20) NOT NULL,
    active      BOOLEAN     NOT NULL,
    PRIMARY KEY (account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- unique (account_id, cycle): re-running or resuming a bill run can never double-bill
CREATE TABLE invoices (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    account_id  VARCHAR(20)    NOT NULL,
    cycle       VARCHAR(7)     NOT NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    created_at  DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_invoices_account_cycle UNIQUE (account_id, cycle),
    INDEX idx_invoices_cycle (cycle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE background_jobs (
    id                BINARY(16)   NOT NULL,
    type              VARCHAR(30)  NOT NULL,
    parameters        VARCHAR(50)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    cancel_requested  BOOLEAN      NOT NULL,
    total_items       BIGINT       NOT NULL,
    processed_items   BIGINT       NOT NULL,
    failed_items      BIGINT       NOT NULL,
    checkpoint        VARCHAR(50)  NULL,
    message           VARCHAR(500) NULL,
    requested_by      VARCHAR(50)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    started_at        DATETIME(6)  NULL,
    finished_at       DATETIME(6)  NULL,
    heartbeat_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    INDEX idx_jobs_status (status),
    INDEX idx_jobs_type_params (type, parameters)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
