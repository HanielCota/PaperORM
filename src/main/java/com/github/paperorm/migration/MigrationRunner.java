package com.github.paperorm.migration;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.exception.ConnectionException;
import java.sql.SQLException;
import java.util.*;

public final class MigrationRunner {

  public static List<Migration> loadFromClasspath(ClassLoader classLoader, String directoryPath) {
    var prefix = directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
    var migrations = new ArrayList<Migration>();
    int version = 1;
    while (true) {
      var resourcePath = prefix + "V" + version + ".sql";
      try (var is = classLoader.getResourceAsStream(resourcePath)) {
        if (is == null) {
          break;
        }
        var sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        migrations.add(new Migration(version, "Migration V" + version, sql));
        version++;
      } catch (java.io.IOException exception) {
        throw new RuntimeException("Failed to read migration resource: " + resourcePath, exception);
      }
    }
    return migrations;
  }

  public static List<Migration> loadFromDirectory(java.nio.file.Path directory) {
    if (!java.nio.file.Files.isDirectory(directory)) {
      return Collections.emptyList();
    }
    var migrations = new ArrayList<Migration>();
    int version = 1;
    while (true) {
      var file = directory.resolve("V" + version + ".sql");
      if (!java.nio.file.Files.exists(file)) {
        break;
      }
      try {
        var sql = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        migrations.add(new Migration(version, "Migration V" + version, sql));
        version++;
      } catch (java.io.IOException exception) {
        throw new RuntimeException("Failed to read migration file: " + file, exception);
      }
    }
    return migrations;
  }

  public void run(DatabaseConnection connection, List<Migration> migrations) {
    ensureMigrationTable(connection);

    var appliedVersions = loadAppliedVersions(connection);

    var pendingMigrations =
        migrations.stream()
            .filter(migration -> !appliedVersions.contains(migration.version()))
            .sorted(java.util.Comparator.comparingInt(Migration::version))
            .toList();

    for (var migration : pendingMigrations) {
      applyMigration(connection, migration);
    }
  }

  private void ensureMigrationTable(DatabaseConnection connection) {
    var sql =
        """
                CREATE TABLE IF NOT EXISTS paper_orm_migrations (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at INTEGER NOT NULL DEFAULT (unixepoch())
                )
                """;

    try {
      connection.execute(sql);
    } catch (SQLException exception) {
      throw new ConnectionException("Failed to create migration table", exception);
    }
  }

  private Set<Integer> loadAppliedVersions(DatabaseConnection connection) {
    var sql = "SELECT version FROM paper_orm_migrations";
    var versions = new HashSet<Integer>();

    try (var conn = connection.openConnection();
        var stmt = conn.createStatement();
        var resultSet = stmt.executeQuery(sql)) {
      while (resultSet.next()) {
        versions.add(resultSet.getInt("version"));
      }
    } catch (SQLException exception) {
      throw new ConnectionException("Failed to load applied migrations", exception);
    }

    return versions;
  }

  private void applyMigration(DatabaseConnection connection, Migration migration) {
    connection.runInTransaction(
        txConnection -> {
          try (var statement = txConnection.createStatement()) {
            statement.executeUpdate(migration.sql());
          }

          var insertSql = "INSERT INTO paper_orm_migrations (version, description) VALUES (?, ?)";
          try (var statement = txConnection.prepareStatement(insertSql)) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.executeUpdate();
          }
        });
  }
}
