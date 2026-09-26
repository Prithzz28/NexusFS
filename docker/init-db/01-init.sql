-- NexusFS Metadata Database Initialization
-- This script runs on first PostgreSQL startup

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- The schema is managed by Hibernate auto-DDL in development.
-- In production, use Flyway or Liquibase migrations.
-- This file ensures the database and extensions are ready.
