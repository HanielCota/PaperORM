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

  String selectAllWithCondition(EntityMetadata metadata, String whereClause);

  default String selectAllWithCondition(
      EntityMetadata metadata, String whereClause, String baseSql) {
    return appendWhereClause(baseSql, whereClause);
  }

  String addColumn(String tableName, com.github.paperorm.mapping.ColumnMetadata column);

  String quoteIdentifier(String identifier);

  String currentTimestampDefault();

  String createMigrationTable();

  private static String appendWhereClause(String baseSql, String whereClause) {
    if (whereClause == null) {
      return baseSql;
    }
    var trimmed = whereClause.trim();
    if (trimmed.isEmpty()) {
      return baseSql;
    }
    var upper = trimmed.toUpperCase();
    if (upper.startsWith("ORDER BY")
        || upper.startsWith("LIMIT")
        || upper.startsWith("OFFSET")
        || upper.startsWith("WHERE ")) {
      return baseSql + " " + whereClause;
    }
    return baseSql + " WHERE " + whereClause;
  }
}
