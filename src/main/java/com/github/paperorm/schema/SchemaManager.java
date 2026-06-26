package com.github.paperorm.schema;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.exception.OrmException;
import com.github.paperorm.mapping.ColumnMetadata;
import com.github.paperorm.mapping.EntityMetadata;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class SchemaManager {

  private static final Object SCHEMA_LOCK = new Object();

  private final DatabaseConnection connection;
  private final SqlDialect dialect;

  public void ensureTable(EntityMetadata metadata) {
    synchronized (SCHEMA_LOCK) {
      execute(this.dialect.createTable(metadata));
      synchronizeColumns(metadata);
      createIndexes(metadata);
    }
  }

  /**
   * Drops columns that exist in the database but are no longer present in the entity metadata.
   *
   * <p>This is a destructive operation that can lead to data loss. Call it explicitly only when you
   * are sure that the obsolete columns should be removed.
   */
  public void dropObsoleteColumns(EntityMetadata metadata) {
    synchronized (SCHEMA_LOCK) {
      var tableName = metadata.tableName();
      var existingColumns = loadExistingColumns(tableName);
      var entityColumns = new HashSet<String>();
      for (var column : metadata.columns()) {
        entityColumns.add(column.columnName().toLowerCase());
      }

      for (var existing : existingColumns) {
        if (entityColumns.contains(existing)) {
          continue;
        }
        var sql =
            "ALTER TABLE "
                + this.dialect.quoteIdentifier(tableName)
                + " DROP COLUMN "
                + this.dialect.quoteIdentifier(existing);
        execute(sql);
      }
    }
  }

  private void createIndexes(EntityMetadata metadata) {
    var tableName = metadata.tableName();

    for (var column : metadata.columns()) {
      if (!column.indexed()) {
        continue;
      }

      var indexName = column.indexName();
      if (indexName == null || indexName.isBlank()) {
        indexName = "idx_" + tableName + "_" + column.columnName();
      }

      var createIndexSql =
          "CREATE INDEX IF NOT EXISTS %s ON %s (%s)"
              .formatted(
                  this.dialect.quoteIdentifier(indexName),
                  this.dialect.quoteIdentifier(tableName),
                  this.dialect.quoteIdentifier(column.columnName()));
      execute(createIndexSql);
    }
  }

  private void synchronizeColumns(EntityMetadata metadata) {
    var tableName = metadata.tableName();
    var existingColumns = loadExistingColumns(tableName);

    if (existingColumns.isEmpty()) {
      return;
    }

    for (var column : metadata.columns()) {
      var columnName = column.columnName().toLowerCase();
      if (existingColumns.contains(columnName)) {
        continue;
      }

      migrateColumn(tableName, column);
    }
  }

  private void migrateColumn(String tableName, ColumnMetadata column) {
    var alterSql = this.dialect.addColumn(tableName, column);
    try {
      execute(alterSql);
      return;
    } catch (OrmException exception) {
      if (column.nullable() || !column.autoIncrement()) {
        throw exception;
      }
    }

    var fallbackSql = this.dialect.addColumn(tableName, column, true);
    execute(fallbackSql);
  }

  private Set<String> loadExistingColumns(String tableName) {
    var columns = new HashSet<String>();

    try (var conn = this.connection.openConnection()) {
      var dbMeta = conn.getMetaData();

      tryLoadColumns(dbMeta, tableName, columns);
      if (columns.isEmpty()) {
        tryLoadColumns(dbMeta, tableName.toLowerCase(), columns);
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to load existing columns for " + tableName, exception);
    }

    return columns;
  }

  private void tryLoadColumns(DatabaseMetaData dbMeta, String tableName, Set<String> target)
      throws SQLException {
    try (var rs = dbMeta.getColumns(null, null, tableName, null)) {
      while (rs.next()) {
        var colName = rs.getString("COLUMN_NAME");
        if (colName != null) {
          target.add(colName.toLowerCase());
        }
      }
    }
  }

  private void execute(String sql) {
    try {
      this.connection.execute(sql);
    } catch (SQLException exception) {
      throw new OrmException("Failed to execute DDL: " + sql, exception);
    }
  }
}
