package com.github.paperorm.migration;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.exception.ConnectionException;
import java.sql.SQLException;
import java.util.*;

public final class MigrationRunner {

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
