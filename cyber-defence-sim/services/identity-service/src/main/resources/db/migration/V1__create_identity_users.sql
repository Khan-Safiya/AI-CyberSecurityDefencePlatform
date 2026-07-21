CREATE SCHEMA IF NOT EXISTS identity_service;

CREATE TABLE IF NOT EXISTS identity_service.platform_users (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    roles VARCHAR(240) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_platform_users_username
    ON identity_service.platform_users (username);
