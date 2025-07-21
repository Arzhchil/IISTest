package com.IIS.testTask.repository;

/**
 * Интерфейс репозитория для работы с таблицей Positions.
 * Определяет методы экспорта данных в XML и синхронизации из XML.
 */
public interface PositionRepository {
    /**
     * Экспортирует данные из таблицы Positions в XML-файл.
     *
     * @param filePath Путь к целевому XML-файлу (включая имя файла).
     *                 Формат файла: XML со структурой <positions> + <position>.
     *                 В файл записываются поля DepCode, DepJob, Description.
     * @throws RuntimeException Если произошла ошибка при создании директории или записи файла.
     */
    void exportToXml(String filePath);

    /**
     * Синхронизирует данные в таблице Positions с содержимым XML-файла.
     *
     * @param filePath Путь к XML-файлу (включая имя файла).
     *                 Файл должен соответствовать структуре, созданной методом exportToXml.
     * @throws Exception Если файл не существует, содержит дубликаты ключей или произошли ошибки БД.
     * @throws java.io.FileNotFoundException Если указанный XML-файл отсутствует.
     * @throws IllegalArgumentException Если XML-файл пустой.
     * @throws IllegalStateException Если в XML-файле найдены записи с одинаковым натуральным ключом.
     */
    void syncFromXml(String filePath) throws Exception;
}
