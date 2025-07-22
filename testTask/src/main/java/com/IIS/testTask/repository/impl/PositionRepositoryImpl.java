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
    public void exportToXml(String filePath) throws Exception {
        List<Position> positionList = fetchPosition();
        createDirectoryIfNotExists(filePath);
        Document xmlDocument = buildXmlDocument(positionList);
        saveXmlDocument(xmlDocument, filePath);
    }

    /**
     * Получает данные из таблицы Positions.
     *
     * @return Список записей с полями DepCode, DepJob, Description.
     */
    private List<Position> fetchPosition() {
        String sql = "SELECT DepCode, DepJob, Description FROM Positions";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Position(
                rs.getString("DepCode"),
                rs.getString("DepJob"),
                rs.getString("Description")
        ));
    }

    /**
     * Создаёт директорию для файла, если она не существует.
     *
     * @param filePath Путь к файлу.
     * @throws RuntimeException Если директория не создана.
     */
    private void createDirectoryIfNotExists(String filePath) {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (!created) {
                log.error("Не удалось создать директорию: {}", parentDir.getAbsolutePath());
                throw new RuntimeException("Не удалось создать директорию для сохранения XML-файла: " + parentDir.getAbsolutePath());
            }
        }
    }

    /**
     * Строит XML-документ из данных.
     *
     * @param positionDataList Список записей.
     * @return Готовый XML-документ.
     * @throws Exception При ошибке создания XML.
     */
    private Document buildXmlDocument(List<Position> positionDataList) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = doc.createElement("positions");
        doc.appendChild(root);

        for (Position data : positionDataList) {
            Element position = doc.createElement("position");
            addElement(doc, position, "depCode", data.depCode());
            addElement(doc, position, "depJob", data.depJob());
            addElement(doc, position, "description", data.description());
            root.appendChild(position);
        }

        return doc;
    }

    /**
     * Сохраняет XML-документ в файл.
     *
     * @param doc      XML-документ.
     * @param filePath Путь к файлу.
     * @throws Exception При ошибке записи.
     */
    private void saveXmlDocument(Document doc, String filePath) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filePath));
        transformer.transform(source, result);
        log.info("XML-файл успешно создан: {}", filePath);
    }

    /**
     * Добавляет элемент с текстовым содержимым в XML-документ.
     *
     * @param doc     XML-документ.
     * @param parent  Родительский элемент.
     * @param tagName Имя нового элемента.
     * @param value   Текстовое значение элемента.
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
     * @throws Exception                Если произошли ошибки синхронизации или БД.
     * @throws FileNotFoundException    Если файл не существует.
     * @throws IllegalArgumentException Если XML-файл пуст.
     * @throws IllegalStateException    Если в XML-файле есть дубликаты ключей.
     * @transactional Операция выполняется в транзакции. При ошибке изменения откатываются.
     */
    @Override
    @Transactional
    public void syncFromXml(String filePath) throws Exception {
        validateXmlFile(filePath);
        Map<String, Position> xmlPositions = parseXmlFile(filePath);
        createTempTableAndInsertData(xmlPositions);
        deleteUnmatchedRows();
        upsertNewOrUpdatedRows(xmlPositions);
        log.info("Синхронизация завершена: {}", filePath);
    }

    /**
     * Проверяет существование и корректность XML-файла.
     *
     * @param filePath Путь к файлу.
     * @throws FileNotFoundException    Если файл не существует.
     * @throws IllegalArgumentException Если файл пуст.
     */
    private void validateXmlFile(String filePath) throws FileNotFoundException {
        File xmlFile = new File(filePath);
        if (!xmlFile.exists()) {
            throw new FileNotFoundException("Файл не найден: " + filePath);
        }
    }

    /**
     * Парсит XML-файл и проверяет уникальность натурального ключа.
     *
     * @param filePath Путь к файлу.
     * @return Карта с уникальными записями.
     * @throws IllegalStateException Если найдены дубликаты.
     */
    private Map<String, Position> parseXmlFile(String filePath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(filePath));

        NodeList positionNodes = doc.getElementsByTagName("position");
        if (positionNodes.getLength() == 0) {
            throw new IllegalArgumentException("XML-файл пуст. Синхронизация невозможна.");
        }

        Map<String, Position> xmlPositions = new HashMap<>();
        for (int i = 0; i < positionNodes.getLength(); i++) {
            Element positionNode = (Element) positionNodes.item(i);
            String depCode = getXmlElementTextContent(positionNode, "depCode");
            String depJob = getXmlElementTextContent(positionNode, "depJob");
            String naturalKey = depCode + ":" + depJob;

            if (xmlPositions.containsKey(naturalKey)) {
                throw new IllegalStateException("Дублирующийся натуральный ключ в XML: " + naturalKey);
            }

            xmlPositions.put(naturalKey, new Position(
                    depCode,
                    depJob,
                    getXmlElementTextContent(positionNode, "description")
            ));
        }

        return xmlPositions;
    }

    /**
     * Возвращает текстовое содержимое элемента XML.
     *
     * @param parent  Родительский элемент.
     * @param tagName Имя элемента.
     * @return Текстовое значение элемента.
     */
    private String getXmlElementTextContent(Element parent, String tagName) {
        return parent.getElementsByTagName(tagName).item(0).getTextContent();
    }

    /**
     * Создаёт временную таблицу и заполняет её данными из XML.
     *
     * @param xmlPositions Данные из XML.
     */
    private void createTempTableAndInsertData(Map<String, Position> xmlPositions) {
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
    }

    /**
     * Удаляет записи из Positions, которых нет в XML.
     */
    private void deleteUnmatchedRows() {
        String deleteSql = "DELETE FROM Positions p " +
                "WHERE NOT EXISTS (SELECT 1 FROM TempPositions t " +
                "                  WHERE p.DepCode = t.DepCode AND p.DepJob = t.DepJob)";
        int deletedRows = jdbcTemplate.update(deleteSql);
        log.debug("Удалено записей: {}", deletedRows);
    }

    /**
     * Вставляет/обновляет записи из XML в Positions.
     *
     * @param xmlPositions Данные из XML.
     */
    private void upsertNewOrUpdatedRows(Map<String, Position> xmlPositions) {
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
    }
}
