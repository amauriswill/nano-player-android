@echo off
set "JAVA_HOME=C:\Users\Amauris Willmore\AppData\Local\Temp\jdk17\jdk-17.0.10+7"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "C:\Users\Amauris Willmore\Documents\ipod-nano-android"
call gradlew.bat %*
