@echo off
:: Run all backend tests (mock-auth + bff + resource-server).
:: Usage:  scripts\start-backend.bat
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

cd /d "%ROOT_DIR%\backend"
echo Running all backend modules (mock-auth, bff, resource-server)...
echo Run each service individually with start-mock-auth.bat, start-bff.bat, start-resource-server.bat.
echo.
echo Tip: open three separate Command Prompt windows and run each script.
pause
