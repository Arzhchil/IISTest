package com.IIS.testTask.model;

import java.util.Objects;

/**
 * Record, представляющий запись из таблицы Positions.
 * Используется для хранения данных о должности и её описании.
 *
 * @param depCode   Код отдела (натуральный ключ).
 * @param depJob    Название должности (натуральный ключ).
 * @param description Описание должности.
 */
public record Position(String depCode, String depJob, String description) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return Objects.equals(depCode, position.depCode) &&
                Objects.equals(depJob, position.depJob);
    }

    @Override
    public int hashCode() {
        return Objects.hash(depCode, depJob);
    }
}
