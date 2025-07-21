package com.IIS.testTask.repository.impl;

import com.IIS.testTask.model.Position;
import com.IIS.testTask.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Реализация репозитория для работы с таблицей Positions.
 * Использует JDBC (JdbcTemplate) для взаимодействия с БД и XML DOM для обработки файлов.
 *
 * @see PositionRepository
 */
@Slf4j
@RequiredArgsConstructor
@Repository
public class PositionRepositoryImpl implements PositionRepository {
    private final JdbcTemplate jdbcTemplate;

    /**
     * Экспортирует данные из таблицы Positions в XML-файл.
     *
     * <p>Этапы:
     * 1. Выполняет SQL-запрос для получения данных (DepCode, DepJob, Description).
     * 2. Создаёт директорию, если она отсутствует.
     * 3. Генерирует XML-документ с корневым элементом <positions>.
     * 4. Сохраняет документ в указанный файл.
     *
     * @param filePath Путь к целевому XML-файлу.
     * @throws RuntimeException Если произошла ошибка при создании директории или записи файла.
     * @see #addElement(Document, Element, String, String)
     */
    @Override
    public void exportToXml(String filePath) {
        String sql = "SELECT DepCode, DepJob, Description FROM Positions";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    log.error("Не удалось создать директорию: {}", parentDir.getAbsolutePath());
                    throw new RuntimeException("Не удалось создать директорию для сохранения XML-файла: " + parentDir.getAbsolutePath());
                }
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElement("positions");
            doc.appendChild(root);

            for (Map<String, Object> row : rows) {
                Element position = doc.createElement("position");
                addElement(doc, position, "depCode", row.get("DepCode").toString());
                addElement(doc, position, "depJob", row.get("DepJob").toString());
                addElement(doc, position, "description", row.get("Description").toString());
                root.appendChild(position);
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(file);
            transformer.transform(source, result);

            log.info("XML-файл успешно создан: {}", filePath);
        } catch (Exception e) {
            log.error("Ошибка при выгрузке данных в XML: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка выгрузки данных в XML", e);
        }
    }

    /**
     * Добавляет элемент с текстовым содержимым в XML-документ.
     *
     * @param doc      XML-документ.
     * @param parent   Родительский элемент.
     * @param tagName  Имя нового элемента.
     * @param value    Текстовое значение элемента.
     */
    private void addElement(Document doc, Element parent, String tagName, String value) {
        Element element = doc.createElement(tagName);
        element.setTextContent(value);
        parent.appendChild(element);
    }

    /**
     * Синхронизирует данные в таблице Positions с содержимым XML-файла.
     *
     * <p>Этапы:
     * 1. Проверяет существование XML-файла.
     * 2. Парсит XML-файл и проверяет уникальность натурального ключа (DepCode + DepJob).
     * 3. Создаёт временную таблицу TempPositions и заполняет её данными из XML.
     * 4. Удаляет из Positions записи, отсутствующие в TempPositions.
     * 5. Вставляет/обновляет записи из TempPositions в Positions.
     *
     * @param filePath Путь к XML-файлу.
     * @throws Exception Если произошли ошибки синхронизации или БД.
     * @throws FileNotFoundException Если файл не существует.
     * @throws IllegalArgumentException Если XML-файл пуст.
     * @throws IllegalStateException Если в XML-файле есть дубликаты ключей.
     * @transactional Операция выполняется в транзакции. При ошибке изменения откатываются.
     */
    @Override
    @Transactional
    public void syncFromXml(String filePath) throws Exception {
        File xmlFile = new File(filePath);
        if (!xmlFile.exists()) {
            throw new FileNotFoundException("Файл не найден: " + filePath);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);

        NodeList positionNodes = doc.getElementsByTagName("position");
        if (positionNodes.getLength() == 0) {
            throw new IllegalArgumentException("XML-файл пуст. Синхронизация невозможна.");
        }
        Map<String, Position> xmlPositions = new HashMap<>();

        for (int i = 0; i < positionNodes.getLength(); i++) {
            Element positionNode = (Element) positionNodes.item(i);
            String depCode = positionNode.getElementsByTagName("depCode").item(0).getTextContent();
            String depJob = positionNode.getElementsByTagName("depJob").item(0).getTextContent();
            String naturalKey = depCode + ":" + depJob;

            if (xmlPositions.containsKey(naturalKey)) {
                throw new IllegalStateException("Дублирующийся натуральный ключ в XML: " + naturalKey);
            }

            xmlPositions.put(naturalKey, new Position(
                    depCode,
                    depJob,
                    positionNode.getElementsByTagName("description").item(0).getTextContent()
            ));
        }

        String createTempTableSql = "CREATE TEMP TABLE TempPositions (DepCode VARCHAR(20), " +
                "DepJob VARCHAR(100), " +
                "Description VARCHAR(255)) ON COMMIT DROP";
        jdbcTemplate.update(createTempTableSql);

        String insertSql = "INSERT INTO TempPositions (DepCode, DepJob, Description) VALUES (?, ?, ?)";
        List<Object[]> batchArgs = xmlPositions.values().stream()
                .map(position -> new Object[]{
                        position.depCode(),
                        position.depJob(),
                        position.description()
                })
                .toList();

        jdbcTemplate.batchUpdate(insertSql, batchArgs);
        log.debug("Временная таблица заполнена: {} записей", batchArgs.size());

        String deleteSql = "DELETE FROM Positions p " +
                "WHERE NOT EXISTS (SELECT 1 FROM TempPositions t " +
                "                  WHERE p.DepCode = t.DepCode AND p.DepJob = t.DepJob)";
        int deletedRows = jdbcTemplate.update(deleteSql);
        log.debug("Удалено записей: {}", deletedRows);

        String upsertSql = "INSERT INTO Positions (DepCode, DepJob, Description) VALUES (?, ?, ?) " +
                "ON CONFLICT (DepCode, DepJob) DO UPDATE SET Description = EXCLUDED.Description";

        int insertedUpdatedRows = Arrays.stream(
                jdbcTemplate.batchUpdate(
                        upsertSql,
                        xmlPositions.values().stream()
                                .map(position -> new Object[]{
                                        position.depCode(),
                                        position.depJob(),
                                        position.description()
                                })
                                .toList()
                )
        ).sum();

        log.debug("Вставлено/обновлено записей: {}", insertedUpdatedRows);

        log.info("Синхронизация завершена: {}", filePath);
    }
}
