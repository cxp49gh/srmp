# syntax=docker/dockerfile:1.4
FROM postgis/postgis:15-3.4

# Install pgvector for AI vector knowledge search
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    gnupg \
    && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add - \
    && echo "deb http://apt.postgresql.org/pub/repos/apt $(. /etc/os-release && echo $VERSION_CODENAME)-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y postgresql-15-pgvector \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
