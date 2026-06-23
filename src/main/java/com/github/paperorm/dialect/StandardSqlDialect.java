package com.github.paperorm.dialect;

import com.github.paperorm.mapping.ColumnMetadata;
import com.github.paperorm.mapping.EntityMetadata;
import java.util.Objects;
import java.util.StringJoiner;

public record StandardSqlDialect(DatabaseType databaseType) implements SqlDialect {

  public enum DatabaseType {
    SQLITE,
    MYSQL
  }

  public StandardSqlDialect() {
    this(DatabaseType.SQLITE);
  }

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
        definition
            .append(" PRIMARY KEY ")
            .append(databaseType == DatabaseType.MYSQL ? "AUTO_INCREMENT" : "AUTOINCREMENT");
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
  public String selectAllWithCondition(EntityMetadata metadata, String whereClause) {
    return selectAllWithCondition(metadata, whereClause, selectAll(metadata));
  }

  @Override
  public String quoteIdentifier(String identifier) {
    Objects.requireNonNull(identifier, "identifier");
    if (databaseType == DatabaseType.MYSQL) {
      return "`" + identifier.replace("`", "``") + "`";
    }
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  @Override
  public String currentTimestampDefault() {
    return databaseType == DatabaseType.MYSQL ? "UNIX_TIMESTAMP()" : "unixepoch()";
  }

  @Override
  public String createMigrationTable() {
    return "CREATE TABLE IF NOT EXISTS "
        + quoteIdentifier("paper_orm_migrations")
        + " (version INTEGER PRIMARY KEY, description TEXT NOT NULL, applied_at INTEGER NOT NULL DEFAULT ("
        + currentTimestampDefault()
        + "))";
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

    if (column.unique() && databaseType == DatabaseType.MYSQL) {
      def.append(" UNIQUE");
    }

    return def.toString();
  }
}
