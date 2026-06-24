package com.github.paperorm.dialect;

import com.github.paperorm.mapping.ColumnMetadata;
import com.github.paperorm.mapping.EntityMetadata;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractSqlDialect implements SqlDialect {

  private final Map<EntityMetadata, String> insertCache = new ConcurrentHashMap<>();
  private final Map<EntityMetadata, String> updateCache = new ConcurrentHashMap<>();
  private final Map<EntityMetadata, String> deleteByIdCache = new ConcurrentHashMap<>();
  private final Map<EntityMetadata, String> selectAllCache = new ConcurrentHashMap<>();
  private final Map<EntityMetadata, String> selectByIdCache = new ConcurrentHashMap<>();
  private final Map<EntityMetadata, String> existsByIdCache = new ConcurrentHashMap<>();
  private final Map<EntityMetadata, String> createTableCache = new ConcurrentHashMap<>();

  protected abstract String openingQuote();

  protected abstract String closingQuote();

  protected abstract String autoIncrementKeyword();

  protected abstract String identityColumnSuffix(ColumnMetadata column);

  protected boolean includeUniqueInAddColumn() {
    return false;
  }

  @Override
  public String createTable(EntityMetadata metadata) {
    return createTableCache.computeIfAbsent(
        metadata,
        m -> {
          var sql = new StringBuilder();
          sql.append("CREATE TABLE IF NOT EXISTS ")
              .append(quoteIdentifier(m.tableName()))
              .append(" (");

          var definitions = new StringJoiner(", ");

          for (var column : m.columns()) {
            definitions.add(columnDefinition(column));
          }

          sql.append(definitions).append(")");
          return sql.toString();
        });
  }

  @Override
  public String insert(EntityMetadata metadata) {
    return insertCache.computeIfAbsent(
        metadata,
        m -> {
          var columns = new StringJoiner(", ");
          var placeholders = new StringJoiner(", ");

          for (var column : m.columns()) {
            if (column.autoIncrement()) {
              continue;
            }
            columns.add(quoteIdentifier(column.columnName()));
            placeholders.add("?");
          }

          return "INSERT INTO %s (%s) VALUES (%s)"
              .formatted(quoteIdentifier(m.tableName()), columns, placeholders);
        });
  }

  @Override
  public String update(EntityMetadata metadata) {
    return updateCache.computeIfAbsent(
        metadata,
        m -> {
          var setClause = new StringJoiner(", ");

          for (var column : m.columns()) {
            if (column.id()) {
              continue;
            }
            setClause.add(quoteIdentifier(column.columnName()) + " = ?");
          }

          return "UPDATE %s SET %s WHERE %s = ?"
              .formatted(
                  quoteIdentifier(m.tableName()),
                  setClause,
                  quoteIdentifier(m.idColumn().columnName()));
        });
  }

  @Override
  public String deleteById(EntityMetadata metadata) {
    return deleteByIdCache.computeIfAbsent(
        metadata,
        m ->
            "DELETE FROM %s WHERE %s = ?"
                .formatted(
                    quoteIdentifier(m.tableName()), quoteIdentifier(m.idColumn().columnName())));
  }

  @Override
  public String selectAll(EntityMetadata metadata) {
    return selectAllCache.computeIfAbsent(
        metadata,
        m -> {
          var projection = new StringJoiner(", ");
          for (var column : m.columns()) {
            projection.add(quoteIdentifier(column.columnName()));
          }
          return "SELECT %s FROM %s".formatted(projection, quoteIdentifier(m.tableName()));
        });
  }

  @Override
  public String selectById(EntityMetadata metadata) {
    return selectByIdCache.computeIfAbsent(
        metadata,
        m -> "%s WHERE %s = ?".formatted(selectAll(m), quoteIdentifier(m.idColumn().columnName())));
  }

  @Override
  public String selectByColumn(EntityMetadata metadata, String columnName) {
    return "%s WHERE %s = ?".formatted(selectAll(metadata), quoteIdentifier(columnName));
  }

  @Override
  public String existsById(EntityMetadata metadata) {
    return existsByIdCache.computeIfAbsent(
        metadata,
        m ->
            "SELECT 1 FROM %s WHERE %s = ? LIMIT 1"
                .formatted(
                    quoteIdentifier(m.tableName()), quoteIdentifier(m.idColumn().columnName())));
  }

  @Override
  public String selectAllWithCondition(EntityMetadata metadata, String whereClause) {
    return selectAllWithCondition(metadata, whereClause, selectAll(metadata));
  }

  @Override
  public String countWithCondition(EntityMetadata metadata, String whereClause) {
    var baseSql = "SELECT COUNT(*) FROM " + quoteIdentifier(metadata.tableName());
    return selectAllWithCondition(metadata, whereClause, baseSql);
  }

  @Override
  public final String quoteIdentifier(String identifier) {
    Objects.requireNonNull(identifier, "identifier");
    return openingQuote()
        + identifier.replace(closingQuote(), closingQuote() + closingQuote())
        + closingQuote();
  }

  @Override
  public String createMigrationTable() {
    return "CREATE TABLE IF NOT EXISTS %s (version INTEGER PRIMARY KEY, description TEXT NOT NULL, applied_at INTEGER NOT NULL DEFAULT (%s))"
        .formatted(quoteIdentifier("paper_orm_migrations"), currentTimestampDefault());
  }

  @Override
  public String addColumn(String tableName, ColumnMetadata column) {
    var def = new StringBuilder();
    def.append("ALTER TABLE ")
        .append(quoteIdentifier(tableName))
        .append(" ADD COLUMN ")
        .append(quoteIdentifier(column.columnName()))
        .append(" ")
        .append(column.sqlType());

    if (!column.nullable()) {
      def.append(" NOT NULL");
    }

    if (includeUniqueInAddColumn() && column.unique()) {
      def.append(" UNIQUE");
    }

    return def.toString();
  }

  private String columnDefinition(ColumnMetadata column) {
    var def = new StringBuilder();
    def.append(quoteIdentifier(column.columnName())).append(' ').append(column.sqlType());

    if (column.id() && column.autoIncrement()) {
      def.append(' ').append(identityColumnSuffix(column));
      return def.toString();
    }

    if (column.id()) {
      def.append(" PRIMARY KEY");
    }

    if (column.unique()) {
      def.append(" UNIQUE");
    }

    if (!column.nullable() && !column.autoIncrement()) {
      def.append(" NOT NULL");
    }

    return def.toString();
  }
}
