-- Run this ONCE on your dev VM as a privileged Oracle user (SYS or SYSTEM)
-- against the XEPDB1 pluggable database. It creates the bankapp schema that
-- the Spring Boot service will own.
--
-- Docker Oracle XE (port 1521):
--   sqlplus sys/my_secure_password@//localhost:1521/XEPDB1 as sysdba @scripts/setup-oracle.sql
--
-- Standalone Oracle XE Windows installer (port 1522):
--   sqlplus sys/my_secure_password@//localhost:1522/XEPDB1 as sysdba @scripts/setup-oracle.sql
--
-- After this, the backend's Flyway migrations will create the actual tables
-- (BANK_USERS, ACCOUNTS, TRANSACTIONS) inside this schema on first boot.
--
-- NOTE: Connect directly to XEPDB1 in the connection string above.
-- Do NOT run ALTER SESSION SET CONTAINER — you are already in the right container.

-- Drop and recreate so this script is rerunnable. Comment out the DROP if
-- you want to preserve existing data.
BEGIN
  EXECUTE IMMEDIATE 'DROP USER bankapp CASCADE';
EXCEPTION WHEN OTHERS THEN
  IF SQLCODE != -1918 THEN RAISE; END IF;  -- ORA-01918 = user does not exist
END;
/

CREATE USER bankapp IDENTIFIED BY bankapp_password
  DEFAULT TABLESPACE USERS
  QUOTA UNLIMITED ON USERS;

GRANT CREATE SESSION TO bankapp;
GRANT CREATE TABLE TO bankapp;
GRANT CREATE SEQUENCE TO bankapp;
GRANT CREATE VIEW TO bankapp;
GRANT CREATE PROCEDURE TO bankapp;

-- Sanity check
SELECT username, account_status FROM dba_users WHERE username = 'BANKAPP';

EXIT;
