# IISTest

# Как запустить приложение

## Сборка проекта

1. Установите [Java 8+](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html ).
2. Установите [Maven](https://maven.apache.org/download.cgi ).
3. Соберите проект:
   ```bash
   mvn clean install

## Настройки
Пути для экспорта/импорта можно указать в application.properties:

export.filePath=./export.xml
import.filePath=./import.xml

## Создание БД

У вас должна быть установлена СУБД PostgreSQL (15+)

Скрипт schema.sql лежит в папке resources/sql

## Запуск
   ```bash
   .\run.bat -c export/sync -f [path/*.xml]