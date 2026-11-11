CREATE TABLE profile_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    anonymous BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_profile_message_recipient FOREIGN KEY (recipient_id) REFERENCES employee (id),
    CONSTRAINT fk_profile_message_author FOREIGN KEY (author_id) REFERENCES employee (id)
);

CREATE INDEX idx_profile_message_recipient ON profile_message (recipient_id, created_at);
