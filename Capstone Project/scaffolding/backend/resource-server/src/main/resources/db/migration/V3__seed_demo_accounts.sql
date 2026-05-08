-- Demo accounts for development/grading only.
--
-- These accounts are linked to the placeholder admin user seeded in V2.
-- The V2 user (usr_seed_admin) will NOT match your real Google login.
--
-- AFTER your first login, run the following query in SQL*Plus or SQL Developer
-- to find your real USER_ID:
--
--   SELECT USER_ID, EMAIL, SUBJECT FROM BANK_USERS;
--
-- Then add your own accounts by running this block manually, substituting
-- your real USER_ID:
--
--   INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
--   VALUES ('acc_your_checking', '<your-user-id>', 'CHECKING', 'USD', 5000.00);
--
--   INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
--   VALUES ('acc_your_savings',  '<your-user-id>', 'SAVINGS',  'USD', 12500.00);
--   COMMIT;
--
-- The rows below are seeded for the placeholder admin (usr_seed_admin) so that
-- the admin-facing user list has some linked account data to display.

INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
VALUES ('acc_demo_checking', 'usr_seed_admin', 'CHECKING', 'USD', 5000.00);

INSERT INTO ACCOUNTS (ACCOUNT_ID, OWNER_ID, ACCOUNT_TYPE, CURRENCY, BALANCE)
VALUES ('acc_demo_savings', 'usr_seed_admin', 'SAVINGS', 'USD', 12500.00);
