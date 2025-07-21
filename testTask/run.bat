@echo off
setlocal

set JAR_PATH=target\testTask-0.0.1-SNAPSHOT.jar

where java >nul 2>&1
if errorlevel 1 (
    echo Java не установлен. Установите Java 8+
    exit /b 1
)

if not exist "%JAR_PATH%" (
    echo JAR-файл не найден. Сначала соберите проект: mvn clean package
    exit /b 1
)

java -jar "%JAR_PATH%" %*