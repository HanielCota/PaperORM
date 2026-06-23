package com.github.paperorm.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.exception.OrmException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class MigrationRunnerTest {

  @TempDir(cleanup = CleanupMode.NEVER)
  Path tempDir;

  private final List<SqliteDatabaseConnection> connections = new ArrayList<>();
  private final MigrationRunner migrationRunner = new MigrationRunner();

  @AfterEach
  void tearDown() throws Exception {
    connections.forEach(SqliteDatabaseConnection::close);
    connections.clear();
    deleteRecursively(tempDir);
  }

  @Test
  void shouldApplyPendingMigrations() throws SQLException {
    var connection = new SqliteDatabaseConnection(tempDir.resolve("migrations.db"));
    connections.add(connection);
    this.migrationRunner.run(
        connection,
        List.of(
            new Migration(
                1,
                "create users",
                "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL)"),
            new Migration(
                2, "insert default user", "INSERT INTO users (id, name) VALUES (1, 'admin')")));

    try (var stmt = connection.openConnection().createStatement();
        var rs = stmt.executeQuery("SELECT COUNT(*) AS count FROM users")) {
      assertTrue(rs.next());
      assertEquals(1, rs.getInt("count"));
    }
  }

  @Test
  void shouldSkipAlreadyAppliedMigrations() {
    var connection = new SqliteDatabaseConnection(tempDir.resolve("skip.db"));
    connections.add(connection);

    this.migrationRunner.run(
        connection,
        List.of(new Migration(1, "create table", "CREATE TABLE t (id INTEGER PRIMARY KEY)")));
    this.migrationRunner.run(
        connection,
        List.of(new Migration(1, "create table", "CREATE TABLE t (id INTEGER PRIMARY KEY)")));

    var applied = new ArrayList<Integer>();

    try (var stmt = connection.openConnection().createStatement();
        var rs = stmt.executeQuery("SELECT version FROM paper_orm_migrations")) {
      while (rs.next()) {
        applied.add(rs.getInt("version"));
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to read migrations", exception);
    }

    assertEquals(1, applied.size());
    assertEquals(1, applied.get(0));
  }

  @Test
  void shouldFailOnInvalidMigrationSql() {
    var connection = new SqliteDatabaseConnection(tempDir.resolve("fail.db"));
    connections.add(connection);
    var brokenMigration = new Migration(1, "broken", "CREATE TABLET t (id INTEGER PRIMARY KEY)");

    assertThrows(
        OrmException.class, () -> this.migrationRunner.run(connection, List.of(brokenMigration)));
  }

  private static void deleteRecursively(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                  // File may be locked or already deleted during cleanup
                }
              });
    }
  }
}
