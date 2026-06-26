package com.github.paperorm.dialect;

import com.github.paperorm.mapping.ColumnMetadata;
import com.github.paperorm.mapping.EntityMetadata;

public interface SchemaDialect {
  String createTable(EntityMetadata metadata);

  String addColumn(String tableName, ColumnMetadata column);

  default String addColumn(String tableName, ColumnMetadata column, boolean nullable) {
    return addColumn(tableName, column);
  }

  String createMigrationTable();
}
