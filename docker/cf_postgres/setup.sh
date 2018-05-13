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
CREATE SCHEMA node;

CREATE TABLE db.schema_versions (
    schema_version_id       bigserial       NOT NULL,
    schema_tag              varchar(64)     NOT NULL DEFAULT 'node',
    schema_revision         bigint          NOT NULL,
    CONSTRAINT db__pk__schema_versions                  PRIMARY KEY (schema_version_id),
    CONSTRAINT db__tag_revision__schema_versions        UNIQUE (schema_tag, schema_revision)
);
INSERT INTO db.schema_versions (schema_tag, schema_revision) VALUES ('node', 1);

CREATE TABLE node.instance_local_cfg (
    key                     varchar(64)     NOT NULL,
    value                   text            NOT NULL,
    CONSTRAINT node__pk__instance_local_cfg PRIMARY KEY (key)
);

CREATE TABLE node.send_q (
    q_id                    bigserial       NOT NULL,
    a                       text            NOT NULL,
    b                       jsonb           NOT NULL,
    dt                      timestamptz     NOT NULL,
    CONSTRAINT node__pk__send_q                          PRIMARY KEY (q_id),
    CONSTRAINT node__dt__send_q                          UNIQUE (dt, q_id)
);

CREATE OR REPLACE FUNCTION node.dequeue_send_q(addresses text[]) RETURNS SETOF node.send_q AS \$\$
DECLARE c CURSOR FOR SELECT * FROM node.send_q WHERE ARRAY[a] <@ addresses AND dt < current_timestamp FOR UPDATE OF node.send_q;
BEGIN
FOR r IN c LOOP
    DELETE FROM node.send_q WHERE CURRENT OF c;
    RETURN NEXT r;
END LOOP;
END;
\$\$
LANGUAGE plpgsql;

\connect corefabric__config_db;

CREATE SCHEMA db;
CREATE SCHEMA cf;
CREATE SCHEMA config;

CREATE TABLE db.schema_versions (
    schema_version_id       bigserial       NOT NULL,
    schema_tag              varchar(64)     NOT NULL DEFAULT 'config',
    schema_revision         bigint          NOT NULL,
    CONSTRAINT db__pk__schema_versions                  PRIMARY KEY (schema_version_id),
    CONSTRAINT db__tag_revision__schema_versions        UNIQUE (schema_tag, schema_revision)
);
INSERT INTO db.schema_versions (schema_tag, schema_revision) VALUES ('config', 1);
PSQL

service postgresql stop
