package com.IIS.testTask.components;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Класс для хранения конфигурационных параметров импорта данных из XML.
 * Значения загружаются из файла application.properties через @ConfigurationProperties.
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "import")
public class ImportProperties {
    /**
     * Путь к файлу для импорта данных.
     * Пример значения в application.properties:
     * import.filePath=./import.xml
     */
    private String filePath;

}
