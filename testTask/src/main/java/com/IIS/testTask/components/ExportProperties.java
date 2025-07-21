package com.IIS.testTask.components;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Класс для хранения конфигурационных параметров экспорта данных в XML.
 * Значения загружаются из файла application.properties через @ConfigurationProperties.
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "export")
public class ExportProperties {
    /**
     * Путь к файлу для экспорта данных.
     * Пример значения в application.properties:
     * export.filePath=./export.xml
     */
    private String filePath;

}
