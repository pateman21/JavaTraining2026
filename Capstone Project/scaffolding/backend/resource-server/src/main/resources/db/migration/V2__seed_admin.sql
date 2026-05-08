-- Admin seed row for the mock-auth demo user.
-- Spring Authorization Server sets the JWT 'sub' claim to the username,
-- so the subject for the 'admin' user is literally 'admin'.
-- JwtAuthConverter will find this row on first login and assign ROLE_ADMIN.

INSERT INTO BANK_USERS (USER_ID, SUBJECT, EMAIL, DISPLAY_NAME, ROLE)
VALUES ('usr_seed_admin', 'admin', 'admin@mock.local', 'Demo Admin', 'ADMIN');
