CREATE DATABASE creator_db;
CREATE DATABASE payment_db;
CREATE DATABASE settlement_db;
CREATE DATABASE ticket_db;
CREATE DATABASE user_db;
CREATE DATABASE ai_db;
CREATE DATABASE streaming_db;

\c ai_db
CREATE EXTENSION IF NOT EXISTS vector;
\c postgres