@echo off
:: Start standalone Apache Kafka (KRaft mode) on port 9092.
:: Requires Kafka to be installed and storage formatted at C:\kafka.
::
:: First-time setup (one-time only, already done on this machine):
::   1. cd C:\kafka
::   2. .\bin\windows\kafka-storage.bat random-uuid        (copy the UUID)
::   3. .\bin\windows\kafka-storage.bat format -t <UUID> -c config\server.properties --standalone
::
:: Usage:  scripts\start-kafka.bat
:: Stop:   Ctrl-C  (then optionally: .\bin\windows\kafka-server-stop.bat)

setlocal

set "KAFKA_HOME=C:\kafka"

if not exist "%KAFKA_HOME%\bin\windows\kafka-server-start.bat" (
    echo ERROR: Kafka not found at %KAFKA_HOME%
    echo Install Kafka 3.x or 4.x and extract to C:\kafka, then run:
    echo   .\bin\windows\kafka-storage.bat random-uuid
    echo   .\bin\windows\kafka-storage.bat format -t ^<UUID^> -c config\server.properties --standalone
    exit /b 1
)

if not exist "%KAFKA_HOME%\data\meta.properties" (
    echo ERROR: Kafka storage not formatted. Run once:
    echo   cd C:\kafka
    echo   .\bin\windows\kafka-storage.bat random-uuid
    echo   .\bin\windows\kafka-storage.bat format -t ^<UUID^> -c config\server.properties --standalone
    exit /b 1
)

:: Use IntelliJ's JDK if JAVA_HOME is not already set
if "%JAVA_HOME%"=="" (
    for /d %%d in ("%USERPROFILE%\.jdks\ms-*") do set "JAVA_HOME=%%d"
)
if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not set and no JDK found in %USERPROFILE%\.jdks\
    echo Set JAVA_HOME to your JDK 17+ installation directory.
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Using JAVA_HOME: %JAVA_HOME%
echo Starting Kafka broker on localhost:9092
echo Press Ctrl-C to stop.
echo.

cd /d "%KAFKA_HOME%"
.\bin\windows\kafka-server-start.bat config\server.properties
