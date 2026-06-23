package com.github.paperorm.mapping;

import java.util.List;

public record EntityMetadata(
    Class<?> entityClass,
    String tableName,
    List<ColumnMetadata> columns,
    ColumnMetadata idColumn) {}
