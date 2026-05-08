# Setting up Oracle XE for Capstone Backend (CDB root only)

If you cannot use the XEPDB1 pluggable database (PDB) and only have the XE CDB root available, follow these steps to set up your development database:

## 1. Run the XE Setup Script

Open a terminal and run the following command as a privileged user (SYS or SYSTEM):

```
sqlplus sys/<sys-password>@//localhost:1521/XE as sysdba @scripts/setup-oracle-xe.sql
```

This will create the `c##bankapp` user/schema in the XE CDB root.

## 2. Update Backend Configuration

Edit your `.env` file (or `application.yml` if not using `.env`) to use the XE service and the `c##bankapp` user:

```
ORACLE_URL=jdbc:oracle:thin:@//localhost:1521/XE
ORACLE_USERNAME=c##bankapp
ORACLE_PASSWORD=bankapp
```

Or, in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//localhost:1521/XE
    username: c##bankapp
    password: bankapp
```

## 3. Start the Backend

Run your backend as usual. Flyway migrations will run in the `c##bankapp` schema in the XE CDB root.

---

**Note:**
- You must use a common user (name starts with `c##`) in the CDB root.
- Do not use `ALTER SESSION SET CONTAINER = XE`.
- Do not reference `XEPDB1` anywhere in your configuration.
- If you later succeed in creating XEPDB1, you can revert to the original instructions and scripts.
