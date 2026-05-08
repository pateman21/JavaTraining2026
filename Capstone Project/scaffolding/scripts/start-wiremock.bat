@echo off
:: Start WireMock standalone on port 8089 with the stubs in wiremock-stubs\mappings.
:: WireMock impersonates the external Payment Processor for local development.
:: Usage:  scripts\start-wiremock.bat
:: Stop:   Ctrl-C
::
:: First run downloads the WireMock JAR (~25 MB) into scripts\.cache\.
:: Subsequent runs reuse the cached JAR.

setlocal
set "WIREMOCK_VERSION=3.6.0"
set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."
set "CACHE_DIR=%SCRIPT_DIR%.cache"
set "JAR=%CACHE_DIR%\wiremock-standalone-%WIREMOCK_VERSION%.jar"
set "URL=https://repo1.maven.org/maven2/org/wiremock/wiremock-standalone/%WIREMOCK_VERSION%/wiremock-standalone-%WIREMOCK_VERSION%.jar"

if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

if not exist "%JAR%" (
    echo Downloading WireMock %WIREMOCK_VERSION%...
    powershell -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%JAR%'" 
    if errorlevel 1 (
        echo ERROR: Download failed. Check your internet connection or manually place
        echo wiremock-standalone-%WIREMOCK_VERSION%.jar in scripts\.cache\
        exit /b 1
    )
    echo Download complete.
)

cd /d "%ROOT_DIR%"
echo Starting WireMock on http://localhost:8089
echo Stubs from: %ROOT_DIR%\wiremock-stubs\mappings
echo.
java -jar "%JAR%" ^
  --port 8089 ^
  --root-dir wiremock-stubs ^
  --global-response-templating ^
  --verbose
