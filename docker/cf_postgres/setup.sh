#!/bin/sh

# does nothing; your services go here.

service postgresql start

su - postgres -c psql <<PSQL
ALTER ROLE postgres WITH SUPERUSER LOGIN PASSWORD 'password';

CREATE DATABASE corefabric__node_db;

CREATE DATABASE corefabric__config_db;

\connect corefabric__node_db

CREATE SCHEMA db;
CREATE SCHEMA cf;

CREATE TABLE db.schema_versions (
    schema_version_id       bigserial       NOT NULL,
    schema_tag              varchar(64)     NOT NULL DEFAULT 'node',
    schema_revision         bigint          NOT NULL,
    CONSTRAINT pk PRIMARY KEY (schema_version_id),
    CONSTRAINT tag_revision UNIQUE (schema_tag, schema_revision)
);
INSERT INTO db.schema_versions (schema_tag, schema_revision) VALUES ('node', 1);

\connect corefabric__config_db;

CREATE SCHEMA db;
CREATE SCHEMA cf;

CREATE TABLE db.schema_versions (
    schema_version_id       bigserial       NOT NULL,
    schema_tag              varchar(64)     NOT NULL DEFAULT 'config',
    schema_revision         bigint          NOT NULL,
    CONSTRAINT pk PRIMARY KEY (schema_version_id),
    CONSTRAINT tag_revision UNIQUE (schema_tag, schema_revision)
);
INSERT INTO db.schema_versions (schema_tag, schema_revision) VALUES ('config', 1);
PSQL

service postgresql stop
