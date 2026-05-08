-- Run this ONCE on your dev VM as a privileged Oracle user (SYS or SYSTEM)
-- against the XE CDB root (not a pluggable database). It creates the c##bankapp schema
-- that the Spring Boot service will own.
--
--   sqlplus sys/<sys-password>@//localhost:1521/XE as sysdba @scripts/setup-oracle-xe.sql
--
-- After this, the backend's Flyway migrations will create the actual tables
-- (BANK_USERS, ACCOUNTS, TRANSACTIONS) inside this schema on first boot.

-- No ALTER SESSION needed for CDB root

-- Drop and recreate so this script is rerunnable. Comment out the DROP if
-- you want to preserve existing data.
BEGIN
  EXECUTE IMMEDIATE 'DROP USER c##bankapp CASCADE';
EXCEPTION WHEN OTHERS THEN
  IF SQLCODE != -1918 THEN RAISE; END IF;  -- ORA-01918 = user does not exist
END;
/

CREATE USER c##bankapp IDENTIFIED BY bankapp
  DEFAULT TABLESPACE USERS
  QUOTA UNLIMITED ON USERS;

GRANT CREATE SESSION TO c##bankapp;
GRANT CREATE TABLE TO c##bankapp;
GRANT CREATE SEQUENCE TO c##bankapp;
GRANT CREATE VIEW TO c##bankapp;
GRANT CREATE PROCEDURE TO c##bankapp;

-- Sanity check
SELECT username, account_status FROM dba_users WHERE username = 'C##BANKAPP';

EXIT;
