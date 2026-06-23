package com.github.paperorm.dialect;

import com.github.paperorm.mapping.ColumnMetadata;
import com.github.paperorm.mapping.EntityMetadata;
import java.util.Objects;
import java.util.StringJoiner;

public record MySqlDialect() implements SqlDialect {

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
        definition.append(" PRIMARY KEY AUTO_INCREMENT");
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

    return "INSERT INTO %s (%s) VALUES (%s)"
        .formatted(quoteIdentifier(metadata.tableName()), columns, placeholders);
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

    return "UPDATE %s SET %s WHERE %s = ?"
        .formatted(
            quoteIdentifier(metadata.tableName()),
            setClause,
            quoteIdentifier(metadata.idColumn().columnName()));
  }

  @Override
  public String deleteById(EntityMetadata metadata) {
    return "DELETE FROM %s WHERE %s = ?"
        .formatted(
            quoteIdentifier(metadata.tableName()),
            quoteIdentifier(metadata.idColumn().columnName()));
  }

  @Override
  public String selectAll(EntityMetadata metadata) {
    var projection = new StringJoiner(", ");
    for (var column : metadata.columns()) {
      projection.add(quoteIdentifier(column.columnName()));
    }
    return "SELECT %s FROM %s".formatted(projection, quoteIdentifier(metadata.tableName()));
  }

  @Override
  public String selectById(EntityMetadata metadata) {
    return "%s WHERE %s = ?"
        .formatted(selectAll(metadata), quoteIdentifier(metadata.idColumn().columnName()));
  }

  @Override
  public String selectByColumn(EntityMetadata metadata, String columnName) {
    return "%s WHERE %s = ?".formatted(selectAll(metadata), quoteIdentifier(columnName));
  }

  @Override
  public String existsById(EntityMetadata metadata) {
    return "SELECT 1 FROM %s WHERE %s = ? LIMIT 1"
        .formatted(
            quoteIdentifier(metadata.tableName()),
            quoteIdentifier(metadata.idColumn().columnName()));
  }

  @Override
  public String selectAllWithCondition(EntityMetadata metadata, String whereClause) {
    return selectAllWithCondition(metadata, whereClause, selectAll(metadata));
  }

  @Override
  public String quoteIdentifier(String identifier) {
    Objects.requireNonNull(identifier, "identifier");
    return "`" + identifier.replace("`", "``") + "`";
  }

  @Override
  public String currentTimestampDefault() {
    return "UNIX_TIMESTAMP()";
  }

  @Override
  public String createMigrationTable() {
    return "CREATE TABLE IF NOT EXISTS %s (version INTEGER PRIMARY KEY, description TEXT NOT NULL, applied_at INTEGER NOT NULL DEFAULT (%s))"
        .formatted(quoteIdentifier("paper_orm_migrations"), currentTimestampDefault());
  }

  private static String sqlType(ColumnMetadata column) {
    return column.sqlType();
  }

  @Override
  public String addColumn(String tableName, ColumnMetadata column) {
    var def = new StringBuilder();
    def.append("ALTER TABLE ")
        .append(quoteIdentifier(tableName))
        .append(" ADD COLUMN ")
        .append(quoteIdentifier(column.columnName()))
        .append(" ")
        .append(sqlType(column));

    if (!column.nullable()) {
      def.append(" NOT NULL");
    }

    if (column.unique()) {
      def.append(" UNIQUE");
    }

    return def.toString();
  }
}
