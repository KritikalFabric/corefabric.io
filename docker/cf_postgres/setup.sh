#!/bin/sh

# does nothing; your services go here.

service postgresql start

su - postgres -c psql <<SQL
ALTER ROLE postgres WITH SUPERUSER LOGIN PASSWORD 'password';

CREATE DATABASE corefabric__node_db;

CREATE DATABASE corefabric__config_db;
SQL

service postgresql stop
