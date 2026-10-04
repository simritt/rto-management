@echo off
rem One command to connect YOUR MySQL: creates the app user + database, seeds roles, writes .env.
rem   setup-mysql.cmd                     (MySQL 8.0 on port 3307)      setup-mysql.cmd --port=3306 --sample-data
setlocal
set "HERE=%~dp0"
cd /d "%HERE%"
for /d %%D in ("%HERE%.tools\jdk-*") do set "JAVA_HOME=%%D"
if not exist target\rto-management-backend-1.0.0.jar call mvn.cmd -q -DskipTests package
"%JAVA_HOME%\bin\java.exe" -jar target\rto-management-backend-1.0.0.jar --rto.command=setup-mysql %*
endlocal
