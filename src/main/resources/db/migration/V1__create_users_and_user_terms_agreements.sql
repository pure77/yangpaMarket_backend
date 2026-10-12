CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    email VARCHAR(255) NULL,
    password_hash VARCHAR(255) NULL,
    nickname VARCHAR(20) NULL,
    phone VARCHAR(20) NULL,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PROFILE',
    marketing_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    profile_image_url VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_public_id UNIQUE (public_id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone)
);

CREATE TABLE user_terms_agreements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    term_code VARCHAR(100) NOT NULL,
    is_required BOOLEAN NOT NULL,
    agreed BOOLEAN NOT NULL,
    agreed_at TIMESTAMP NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_terms_agreements_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_user_terms_agreements_user_term UNIQUE (user_id, term_code)
);

CREATE INDEX idx_user_terms_agreements_user_id ON user_terms_agreements (user_id);
