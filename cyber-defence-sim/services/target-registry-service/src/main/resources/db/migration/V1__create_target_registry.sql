CREATE TABLE targets (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    mode VARCHAR(50) NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    environment_type VARCHAR(50) NOT NULL,
    max_requests_per_minute INTEGER NOT NULL,
    written_authorization_confirmed BOOLEAN NOT NULL,
    ownership_verification_status VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    verification_token VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE target_allowed_hosts (
    target_id UUID NOT NULL REFERENCES targets(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    host_value VARCHAR(253) NOT NULL,
    PRIMARY KEY (target_id, position)
);

CREATE TABLE target_allowed_paths (
    target_id UUID NOT NULL REFERENCES targets(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    path_value VARCHAR(2048) NOT NULL,
    PRIMARY KEY (target_id, position)
);

CREATE TABLE target_excluded_paths (
    target_id UUID NOT NULL REFERENCES targets(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    path_value VARCHAR(2048) NOT NULL,
    PRIMARY KEY (target_id, position)
);

CREATE TABLE target_allowed_http_methods (
    target_id UUID NOT NULL REFERENCES targets(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    method_value VARCHAR(16) NOT NULL,
    PRIMARY KEY (target_id, position)
);

INSERT INTO targets (
    id, name, description, mode, base_url, environment_type,
    max_requests_per_minute, written_authorization_confirmed,
    ownership_verification_status, status, verification_token, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000101',
    'Built-in sandbox target',
    'Local intentionally vulnerable training target',
    'INTERNAL_SANDBOX',
    'http://target-system-service:8080',
    'SANDBOX',
    600,
    TRUE,
    'VERIFIED',
    'ACTIVE',
    'sandbox-auto-verified',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO target_allowed_hosts (target_id, position, host_value) VALUES
    ('00000000-0000-0000-0000-000000000101', 0, 'target-system-service:8080'),
    ('00000000-0000-0000-0000-000000000101', 1, 'localhost:8104');

INSERT INTO target_allowed_paths (target_id, position, path_value) VALUES
    ('00000000-0000-0000-0000-000000000101', 0, '/demo/**');

INSERT INTO target_allowed_http_methods (target_id, position, method_value) VALUES
    ('00000000-0000-0000-0000-000000000101', 0, 'GET'),
    ('00000000-0000-0000-0000-000000000101', 1, 'POST');
