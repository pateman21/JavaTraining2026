@echo off
:: Start the Resource Server (JWT-secured REST API) on port 8081.
:: Usage:  scripts\start-resource-server.bat
:: Stop:   Ctrl-C

setlocal
set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."

if exist "%ROOT_DIR%\.env" (
    echo Loading env vars from .env
    for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT_DIR%\.env") do (
        if not "%%A"=="" if not "%%A:~0,1%"=="#" set "%%A=%%B"
    )
) else (
    echo No .env found -- using environment variables only.
)

cd /d "%ROOT_DIR%\backend\resource-server"
echo Starting Resource Server on http://localhost:8081 ...
mvn spring-boot:run
