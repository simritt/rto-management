@echo off
rem Starts the API on http://127.0.0.1:8000  (Swagger UI: /docs). Reads settings from .env in this folder.
setlocal
set "HERE=%~dp0"
cd /d "%HERE%"
for /d %%D in ("%HERE%.tools\jdk-*") do set "JAVA_HOME=%%D"
if not exist target\rto-management-backend-1.0.0.jar call mvn.cmd -q -DskipTests package
"%JAVA_HOME%\bin\java.exe" -jar target\rto-management-backend-1.0.0.jar %*
endlocal
