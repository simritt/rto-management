@echo off
rem Runs the project-local Maven with the project-local JDK (nothing is installed system-wide).
rem   mvn.cmd test            mvn.cmd package -DskipTests            mvn.cmd spring-boot:run
setlocal
set "HERE=%~dp0"
for /d %%D in ("%HERE%.tools\jdk-*") do set "JAVA_HOME=%%D"
set "PATH=%JAVA_HOME%\bin;%PATH%"
"%HERE%.tools\apache-maven-3.9.9\bin\mvn.cmd" -Dmaven.repo.local="%HERE%.tools\m2" %*
endlocal
