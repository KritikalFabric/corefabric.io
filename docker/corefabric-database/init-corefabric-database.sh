#!/usr/bin/env bash

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
alter role postgres with superuser login password 'password';

create database corefabric__node_db with encoding 'utf-8';
create database corefabric__cluster_db with encoding 'utf-8';

\connect corefabric__node_db;

\connect corefabric__cluster_db;

\q
EOSQL
