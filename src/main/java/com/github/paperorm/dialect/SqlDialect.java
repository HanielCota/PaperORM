package com.github.paperorm.dialect;

import com.github.paperorm.mapping.EntityMetadata;

public interface SqlDialect {

  String createTable(EntityMetadata metadata);

  String insert(EntityMetadata metadata);

  String update(EntityMetadata metadata);

  String deleteById(EntityMetadata metadata);

  String selectAll(EntityMetadata metadata);

  String selectById(EntityMetadata metadata);

  String selectByColumn(EntityMetadata metadata, String columnName);

  String existsById(EntityMetadata metadata);

  String addColumn(String tableName, com.github.paperorm.mapping.ColumnMetadata column);

  String quoteIdentifier(String identifier);
}
