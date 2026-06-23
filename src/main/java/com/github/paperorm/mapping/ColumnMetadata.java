package com.github.paperorm.mapping;

import java.lang.reflect.Field;

public record ColumnMetadata(
    String columnName,
    Field field,
    boolean id,
    boolean autoIncrement,
    boolean nullable,
    boolean unique,
    int length,
    boolean manyToOne,
    Class<?> referencedClass,
    boolean indexed,
    String indexName,
    String sqlType) {}
