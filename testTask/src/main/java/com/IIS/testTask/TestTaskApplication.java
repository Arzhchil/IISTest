package com.IIS.testTask;

import com.IIS.testTask.components.ExportProperties;
import com.IIS.testTask.components.ImportProperties;
import com.IIS.testTask.repository.PositionRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.File;

/**
 * Главный класс приложения, отвечающий за запуск и обработку командной строки.
 *
 * <p>Приложение поддерживает две основные команды:
 * - <b>export</b>: Экспорт данных из таблицы Positions в XML-файл.
 * - <b>sync</b>: Синхронизация таблицы Positions с данными из XML-файла.
 *
 * <p>Конфигурационные параметры (пути к файлам, логирование, подключение к БД) задаются через `application.properties`.
 *
 * @see PositionRepository
 * @see ExportProperties
 * @see ImportProperties
 */
@Slf4j
@SpringBootApplication
@EnableTransactionManagement
public class TestTaskApplication {

    /**
     * Точка входа в приложение. Обрабатывает командную строку, проверяет параметры,
     * инициализирует Spring-контекст и выполняет выбранную операцию (экспорт/синхронизация).
     *
     * <p>Пример использования:
     * <pre>
     * java -jar target/testTask-0.0.1-SNAPSHOT.jar export ./data/export.xml
     * java -jar target/testTask-0.0.1-SNAPSHOT.jar sync ./data/import.xml
     * </pre>
     *
     * <p>Доступные опции:
     * - <b>-c</b> или <b>--command</b>: Команда (`export` или `sync`).
     * - <b>-f</b> или <b>--file</b>: Путь к XML-файлу.
     * - <b>-h</b> или <b>--help</b>: Вывод помощи.
     *
     * @param args Аргументы командной строки.
     */
    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("c", "command", true, "Команда: 'export' или 'sync'");
        options.addOption("f", "file", true, "Путь к XML файлу");
        options.addOption("h", "help", false, "Вывести помощь");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                formatter.printHelp("java -jar target/testTask-0.0.1-SNAPSHOT.jar", options);
                return;
            }

            if (!cmd.hasOption("c")) {
                System.out.println("Ошибка: Необходимо указать команду (-c)");
                formatter.printHelp("java -jar target/testTask-0.0.1-SNAPSHOT.jar", options);
                return;
            }

            String command = cmd.getOptionValue("c").toLowerCase();
            String filePathFromArgs = cmd.hasOption("f") ? cmd.getOptionValue("f") : null;

            ApplicationContext context = SpringApplication.run(TestTaskApplication.class, args);
            PositionRepository positionRepository = context.getBean(PositionRepository.class);
            ExportProperties exportProperties = context.getBean(ExportProperties.class);
            ImportProperties importProperties = context.getBean(ImportProperties.class);

            String finalFilePath;

            if ("export".equals(command)) {
                finalFilePath = filePathFromArgs != null ? filePathFromArgs : exportProperties.getFilePath();

                if (filePathFromArgs == null && exportProperties.getFilePath() == null) {
                    log.error("Путь для экспорта не указан в конфиге (export.filePath) и не передан через CLI");
                    System.out.println("Ошибка: Путь для экспорта не указан");
                    return;
                }

                File file = new File(finalFilePath);
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    log.error("Не удалось создать директорию: {}", parentDir.getAbsolutePath());
                    System.out.println("Ошибка: Не удалось создать директорию.");
                    return;
                }
            } else if ("sync".equals(command)) {
                finalFilePath = filePathFromArgs != null ? filePathFromArgs : importProperties.getFilePath();

                if (filePathFromArgs == null && importProperties.getFilePath() == null) {
                    log.error("Путь для синхронизации не указан в конфиге (import.filePath) и не передан через CLI");
                    System.out.println("Ошибка: Путь для синхронизации не указан");
                    return;
                }

                if (!new File(finalFilePath).exists()) {
                    log.error("Файл не существует: {}", finalFilePath);
                    System.out.println("Ошибка: Файл не существует.");
                    return;
                }
            } else {
                log.error("Неизвестная команда: {}", command);
                System.out.println("Ошибка: Неизвестная команда.");
                return;
            }

            if ("export".equals(command)) {
                positionRepository.exportToXml(finalFilePath);
                log.info("Экспорт завершён: {}", finalFilePath);
            } else {
                positionRepository.syncFromXml(finalFilePath);
                log.info("Синхронизация завершена: {}", finalFilePath);
            }

            System.out.println("Операция завершена.");
        } catch (Exception e) {
            log.error("Ошибка: {}", e.getMessage(), e);
            formatter.printHelp("java -jar target/testTask-0.0.1-SNAPSHOT.jar", options);
        }
    }
}