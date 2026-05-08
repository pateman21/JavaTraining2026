@echo off
:: Start the Mock Authorization Server (Spring Authorization Server) on port 9000.
:: Usage:  scripts\start-mock-auth.bat
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

cd /d "%ROOT_DIR%\backend\mock-auth"
echo Starting Mock Auth Server on http://localhost:9000 ...
mvn spring-boot:run
