package com.github.paperorm.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.dialect.SqliteDialect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationRunnerTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private MigrationRunner runner;

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    runner = new MigrationRunner(new SqliteDialect());
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  @Test
  void shouldRunSingleMigration() throws Exception {
    var migrations =
        List.of(new Migration(1, "Create users", "CREATE TABLE users (id INTEGER PRIMARY KEY)"));
    runner.run(connection, migrations);

    assertTableExists("users");
    assertMigrationRecorded(1);
  }

  @Test
  void shouldRunMultipleMigrationsInOrder() throws Exception {
    var migrations =
        List.of(
            new Migration(1, "Create t1", "CREATE TABLE t1 (id INTEGER PRIMARY KEY)"),
            new Migration(2, "Create t2", "CREATE TABLE t2 (id INTEGER PRIMARY KEY)"),
            new Migration(3, "Create t3", "CREATE TABLE t3 (id INTEGER PRIMARY KEY)"));

    runner.run(connection, migrations);

    assertTableExists("t1");
    assertTableExists("t2");
    assertTableExists("t3");
    assertMigrationRecorded(1);
    assertMigrationRecorded(2);
    assertMigrationRecorded(3);
  }

  @Test
  void shouldSkipAlreadyAppliedMigrations() throws Exception {
    var migrations = List.of(new Migration(1, "Create a", "CREATE TABLE a (id INTEGER)"));
    runner.run(connection, migrations);

    runner.run(connection, migrations);
    assertTableExists("a");
    assertEquals(1, countMigrationRecords());
  }

  @Test
  void shouldSkipAlreadyAppliedWhenMixed() throws Exception {
    runner.run(connection, List.of(new Migration(1, "First", "CREATE TABLE first (id INTEGER)")));

    var mixed =
        List.of(
            new Migration(1, "First", "CREATE TABLE first (id INTEGER)"),
            new Migration(2, "Second", "CREATE TABLE second (id INTEGER)"));
    runner.run(connection, mixed);

    assertTableExists("first");
    assertTableExists("second");
    assertEquals(2, countMigrationRecords());
  }

  @Test
  void shouldHandleEmptyMigrations() {
    runner.run(connection, List.of());
    assertNotNull(connection);
  }

  @Test
  void shouldLoadFromDirectory() throws Exception {
    var dir = tempDir.resolve("migrations");
    Files.createDirectory(dir);
    Files.writeString(dir.resolve("V1.sql"), "CREATE TABLE from_dir (id INTEGER PRIMARY KEY)");
    Files.writeString(dir.resolve("V2.sql"), "CREATE TABLE from_dir2 (id INTEGER PRIMARY KEY)");

    var loaded = MigrationRunner.loadFromDirectory(dir);
    assertEquals(2, loaded.size());
    assertEquals(1, loaded.get(0).version());
    assertEquals(2, loaded.get(1).version());
  }

  @Test
  void shouldReturnEmptyForNonexistentDirectory() {
    var nonExistent = tempDir.resolve("nonexistent");
    var loaded = MigrationRunner.loadFromDirectory(nonExistent);
    assertTrue(loaded.isEmpty());
  }

  @Test
  void shouldRunMultiStatementMigration() throws Exception {
    var migrations =
        List.of(
            new Migration(
                1,
                "Multi statement",
                "CREATE TABLE m1 (id INTEGER PRIMARY KEY); CREATE TABLE m2 (id INTEGER PRIMARY KEY);"));
    runner.run(connection, migrations);

    assertTableExists("m1");
    assertTableExists("m2");
    assertMigrationRecorded(1);
  }

  @Test
  void shouldHandleGapsInMigrationVersions() throws Exception {
    var dir = tempDir.resolve("gapped_migrations");
    Files.createDirectory(dir);
    Files.writeString(dir.resolve("V1.sql"), "CREATE TABLE gap_one (id INTEGER PRIMARY KEY)");
    Files.writeString(dir.resolve("V3.sql"), "CREATE TABLE gap_three (id INTEGER PRIMARY KEY)");

    var loaded = MigrationRunner.loadFromDirectory(dir);
    assertEquals(2, loaded.size());
    assertEquals(1, loaded.get(0).version());
    assertEquals(3, loaded.get(1).version());

    runner.run(connection, loaded);
    assertTableExists("gap_one");
    assertTableExists("gap_three");
  }

  @Test
  void shouldSplitStatementsRespectingQuotes() {
    var script = "INSERT INTO t VALUES ('a;b'); INSERT INTO t VALUES ('c');";
    var statements = MigrationRunner.splitStatements(script);

    assertEquals(2, statements.size());
    assertEquals("INSERT INTO t VALUES ('a;b')", statements.get(0));
    assertEquals("INSERT INTO t VALUES ('c')", statements.get(1));
  }

  @Test
  void shouldRejectNullArgs() {
    assertThrows(NullPointerException.class, () -> runner.run(null, List.of()));
    assertThrows(NullPointerException.class, () -> runner.run(connection, null));
  }

  @Test
  void shouldHandleOutOfOrderMigrations() throws Exception {
    runner.run(connection, List.of(new Migration(3, "Third", "CREATE TABLE third (id INTEGER)")));

    var all =
        List.of(
            new Migration(1, "First", "CREATE TABLE one (id INTEGER)"),
            new Migration(2, "Second", "CREATE TABLE two (id INTEGER)"),
            new Migration(3, "Third", "CREATE TABLE third (id INTEGER)"));
    runner.run(connection, all);

    assertTableExists("one");
    assertTableExists("two");
    assertTableExists("third");
  }

  @Test
  void shouldRollbackOnFailedMigration() {
    var badMigrations = List.of(new Migration(1, "Bad SQL", "THIS IS NOT VALID SQL"));
    assertThrows(
        com.github.paperorm.exception.ConnectionException.class,
        () -> runner.run(connection, badMigrations));

    assertEquals(0, countMigrationRecords());
  }

  private void assertTableExists(String tableName) throws Exception {
    try (var conn = connection.openConnection()) {
      var meta = conn.getMetaData();
      try (var rs = meta.getTables(null, null, tableName, null)) {
        assertTrue(rs.next(), "Table " + tableName + " should exist");
      }
    }
  }

  private void assertMigrationRecorded(int version) throws Exception {
    try (var conn = connection.openConnection();
        var stmt =
            conn.prepareStatement("SELECT version FROM paper_orm_migrations WHERE version = ?")) {
      stmt.setInt(1, version);
      try (var rs = stmt.executeQuery()) {
        assertTrue(rs.next(), "Migration V" + version + " should be recorded");
      }
    }
  }

  private int countMigrationRecords() {
    try (var conn = connection.openConnection();
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT COUNT(*) FROM paper_orm_migrations")) {
      if (rs.next()) {
        return rs.getInt(1);
      }
    } catch (Exception ignored) {
      // count fails if table doesn't exist yet — return 0
    }
    return 0;
  }
}
