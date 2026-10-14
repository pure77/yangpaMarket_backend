CREATE TABLE user_social_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    email VARCHAR(255) NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_social_accounts_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_user_social_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_user_social_accounts_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_social_accounts_user_id ON user_social_accounts (user_id);
