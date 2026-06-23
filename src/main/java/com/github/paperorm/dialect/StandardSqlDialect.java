package com.github.paperorm.dialect;

import com.github.paperorm.mapping.ColumnMetadata;
import com.github.paperorm.mapping.EntityMetadata;
import com.github.paperorm.mapping.TypeMapper;
import java.util.StringJoiner;

public final class StandardSqlDialect implements SqlDialect {

  @Override
  public String createTable(EntityMetadata metadata) {
    var sql = new StringBuilder();

    sql.append("CREATE TABLE IF NOT EXISTS ")
        .append(quoteIdentifier(metadata.tableName()))
        .append(" (");

    var columnDefinitions = new StringJoiner(", ");

    for (var column : metadata.columns()) {
      var definition = new StringBuilder();
      definition.append(quoteIdentifier(column.columnName())).append(" ").append(sqlType(column));

      if (column.id() && column.autoIncrement()) {
        definition.append(" PRIMARY KEY AUTOINCREMENT");
        columnDefinitions.add(definition);
        continue;
      }

      if (column.id()) {
        definition.append(" PRIMARY KEY");
      }

      if (column.unique()) {
        definition.append(" UNIQUE");
      }

      if (!column.nullable() && !column.autoIncrement()) {
        definition.append(" NOT NULL");
      }

      columnDefinitions.add(definition);
    }

    sql.append(columnDefinitions).append(")");
    return sql.toString();
  }

  @Override
  public String insert(EntityMetadata metadata) {
    var columns = new StringJoiner(", ");
    var placeholders = new StringJoiner(", ");

    for (var column : metadata.columns()) {
      if (column.autoIncrement()) {
        continue;
      }

      columns.add(quoteIdentifier(column.columnName()));
      placeholders.add("?");
    }

    return "INSERT INTO "
        + quoteIdentifier(metadata.tableName())
        + " ("
        + columns
        + ") VALUES ("
        + placeholders
        + ")";
  }

  @Override
  public String update(EntityMetadata metadata) {
    var setClause = new StringJoiner(", ");

    for (var column : metadata.columns()) {
      if (column.id()) {
        continue;
      }

      setClause.add(quoteIdentifier(column.columnName()) + " = ?");
    }

    return "UPDATE "
        + quoteIdentifier(metadata.tableName())
        + " SET "
        + setClause
        + " WHERE "
        + quoteIdentifier(metadata.idColumn().columnName())
        + " = ?";
  }

  @Override
  public String deleteById(EntityMetadata metadata) {
    return "DELETE FROM "
        + quoteIdentifier(metadata.tableName())
        + " WHERE "
        + quoteIdentifier(metadata.idColumn().columnName())
        + " = ?";
  }

  @Override
  public String selectAll(EntityMetadata metadata) {
    var projection = new StringJoiner(", ");
    for (var column : metadata.columns()) {
      projection.add(quoteIdentifier(column.columnName()));
    }
    return "SELECT " + projection + " FROM " + quoteIdentifier(metadata.tableName());
  }

  @Override
  public String selectById(EntityMetadata metadata) {
    return selectAll(metadata)
        + " WHERE "
        + quoteIdentifier(metadata.idColumn().columnName())
        + " = ?";
  }

  @Override
  public String selectByColumn(EntityMetadata metadata, String columnName) {
    return selectAll(metadata) + " WHERE " + quoteIdentifier(columnName) + " = ?";
  }

  @Override
  public String existsById(EntityMetadata metadata) {
    return "SELECT 1 FROM "
        + quoteIdentifier(metadata.tableName())
        + " WHERE "
        + quoteIdentifier(metadata.idColumn().columnName())
        + " = ? LIMIT 1";
  }

  @Override
  public String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  private String sqlType(ColumnMetadata column) {
    var type = column.field().getType();
    if (column.manyToOne()) {
      type = findIdTypeOf(column.referencedClass());
    }

    return TypeMapper.sqlTypeFor(type);
  }

  private Class<?> findIdTypeOf(Class<?> clazz) {
    for (var field : clazz.getDeclaredFields()) {
      if (field.isAnnotationPresent(com.github.paperorm.annotation.Id.class)) {
        return field.getType();
      }
    }
    throw new IllegalArgumentException(
        "Referenced entity " + clazz.getName() + " has no @Id field");
  }

  @Override
  public String addColumn(String tableName, ColumnMetadata column) {
    return "ALTER TABLE "
        + quoteIdentifier(tableName)
        + " ADD COLUMN "
        + quoteIdentifier(column.columnName())
        + " "
        + sqlType(column);
  }
}
