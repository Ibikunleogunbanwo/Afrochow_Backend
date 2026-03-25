CREATE TABLE broadcast_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    title           VARCHAR(255)    NOT NULL,
    message         TEXT            NOT NULL,
    type            VARCHAR(50)     NOT NULL,
    target_audience VARCHAR(20)     NOT NULL,
    recipient_count INT             NOT NULL DEFAULT 0,
    sent_at         DATETIME        NOT NULL,
    sent_by         VARCHAR(255)    NOT NULL,

    PRIMARY KEY (id)
);
