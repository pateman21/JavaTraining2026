@echo off
:: Install (if needed) and start the Vite dev server on port 5173.
:: Usage:  scripts\start-frontend.bat
:: Stop:   Ctrl-C

setlocal
set "SCRIPT_DIR=%~dp0"
set "FRONTEND_DIR=%SCRIPT_DIR%..\frontend"

cd /d "%FRONTEND_DIR%"

if not exist node_modules (
    echo node_modules not found -- running npm install (one-time)...
    npm install --no-audit --no-fund
)

if not exist .env.local (
    echo.
    echo Warning: .env.local does not exist.
    echo Copy .env.example to .env.local and set VITE_GOOGLE_CLIENT_ID before signing in with Google.
    echo.
)

echo Starting Vite dev server on http://localhost:5173 ...
npm run dev
