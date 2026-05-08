@echo off
:: Start the BFF (session-based OAuth2 client + reverse proxy) on port 8080.
:: Usage:  scripts\start-bff.bat
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

cd /d "%ROOT_DIR%\backend\bff"
echo Starting BFF on http://localhost:8080 ...
mvn spring-boot:run
