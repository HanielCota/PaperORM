package com.github.paperorm.migration;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.exception.ConnectionException;
import com.github.paperorm.exception.OrmException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class MigrationRunner {

  private final SqlDialect dialect;

  public static List<Migration> loadFromClasspath(ClassLoader classLoader, String directoryPath) {
    Objects.requireNonNull(classLoader, "classLoader cannot be null");
    Objects.requireNonNull(directoryPath, "directoryPath cannot be null");

    var prefix = directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
    var migrations = new ArrayList<Migration>();

    for (int version = 1; ; version++) {
      var resourcePath = prefix + "V" + version + ".sql";

      try (var is = classLoader.getResourceAsStream(resourcePath)) {
        if (is == null) {
          break;
        }
        var sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        migrations.add(new Migration(version, "Migration V" + version, sql));
      } catch (IOException e) {
        throw new OrmException("Failed to read migration resource: " + resourcePath, e);
      }
    }
    return migrations;
  }

  public static List<Migration> loadFromDirectory(Path directory) {
    Objects.requireNonNull(directory, "directory cannot be null");
    if (!Files.isDirectory(directory)) {
      return Collections.emptyList();
    }

    var migrations = new ArrayList<Migration>();

    for (int version = 1; ; version++) {
      var file = directory.resolve("V" + version + ".sql");
      if (!Files.exists(file)) {
        break;
      }

      try {
        var sql = Files.readString(file, StandardCharsets.UTF_8);
        migrations.add(new Migration(version, "Migration V" + version, sql));
      } catch (IOException e) {
        throw new OrmException("Failed to read migration file: " + file, e);
      }
    }
    return migrations;
  }

  public void run(DatabaseConnection connection, List<Migration> migrations) {
    Objects.requireNonNull(connection, "connection cannot be null");
    Objects.requireNonNull(migrations, "migrations cannot be null");

    ensureMigrationTable(connection);

    var appliedVersions = loadAppliedVersions(connection);
    var pendingMigrations =
        migrations.stream()
            .filter(m -> !appliedVersions.contains(m.version()))
            .sorted(Comparator.comparingInt(Migration::version))
            .toList();

    for (var migration : pendingMigrations) {
      applyMigration(connection, migration);
    }
  }

  private void ensureMigrationTable(DatabaseConnection connection) {
    var sql = this.dialect.createMigrationTable();
    try {
      connection.execute(sql);
    } catch (SQLException e) {
      throw new ConnectionException("Failed to create migration table", e);
    }
  }

  private Set<Integer> loadAppliedVersions(DatabaseConnection connection) {
    var tableName = this.dialect.quoteIdentifier("paper_orm_migrations");
    // tableName is safe: derived from a hardcoded constant, quoted by the dialect
    var sql = "SELECT version FROM " + tableName;
    var versions = new HashSet<Integer>();

    try (var conn = connection.openConnection();
        var stmt = conn.createStatement();
        var resultSet = stmt.executeQuery(sql)) {

      while (resultSet.next()) {
        versions.add(resultSet.getInt("version"));
      }
    } catch (SQLException e) {
      throw new ConnectionException("Failed to load applied migrations", e);
    }

    return versions;
  }

  private void applyMigration(DatabaseConnection connection, Migration migration) {
    var tableName = this.dialect.quoteIdentifier("paper_orm_migrations");
    // tableName is safe: derived from a hardcoded constant, quoted by the dialect;
    // parameters are bound via PreparedStatement
    var insertSql = "INSERT INTO " + tableName + " (version, description) VALUES (?, ?)";
    connection.runInTransaction(
        txConnection -> {
          try (var statement = txConnection.createStatement()) {
            statement.executeUpdate(migration.sql());
          }

          try (var statement = txConnection.prepareStatement(insertSql)) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.executeUpdate();
          }
          return null;
        });
  }
}
