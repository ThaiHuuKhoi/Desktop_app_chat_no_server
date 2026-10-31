ARG MYSQL_VERSION=8.4
FROM mysql:${MYSQL_VERSION}

COPY signaling-server/src/main/resources/schema.sql /docker-entrypoint-initdb.d/001-schema.sql
