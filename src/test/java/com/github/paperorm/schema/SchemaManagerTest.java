package com.github.paperorm.schema;

import static org.junit.jupiter.api.Assertions.*;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.Index;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.dialect.SqliteDialect;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaManagerTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private SqlDialect dialect;
  private SchemaManager schemaManager;

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    dialect = new SqliteDialect();
    schemaManager = new SchemaManager(connection, dialect);
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  @Test
  void shouldCreateTable() throws Exception {
    var metadata = new ReflectionEntityScanner().scan(TestTable.class);
    schemaManager.ensureTable(metadata);

    assertTrue(tableExists("test_table"));

    var columns = loadColumnNames("test_table");
    assertTrue(columns.contains("id"));
    assertTrue(columns.contains("label"));
    assertTrue(columns.contains("value"));
  }

  @Test
  void shouldBeIdempotent() throws Exception {
    var metadata = new ReflectionEntityScanner().scan(TestTable.class);
    schemaManager.ensureTable(metadata);
    schemaManager.ensureTable(metadata);

    assertTrue(tableExists("test_table"));
  }

  @Test
  void shouldAddMissingColumns() throws Exception {
    var metadata = new ReflectionEntityScanner().scan(TestTable.class);
    schemaManager.ensureTable(metadata);

    assertTrue(columnExists("test_table", "label"));

    connection.execute("ALTER TABLE \"test_table\" DROP COLUMN \"label\"");

    schemaManager.ensureTable(metadata);
    assertTrue(columnExists("test_table", "label"));
  }

  @Test
  void shouldCreateIndexes() throws Exception {
    var metadata = new ReflectionEntityScanner().scan(IndexedTable.class);
    schemaManager.ensureTable(metadata);

    assertTrue(indexExists("idx_indexed_table_name"));
  }

  @Test
  void shouldCreateCustomNamedIndex() throws Exception {
    var metadata = new ReflectionEntityScanner().scan(CustomIndexTable.class);
    schemaManager.ensureTable(metadata);

    assertTrue(indexExists("my_custom_index"));
  }

  private boolean tableExists(String tableName) throws Exception {
    try (var conn = connection.openConnection()) {
      var meta = conn.getMetaData();
      try (var rs = meta.getTables(null, null, tableName, null)) {
        return rs.next();
      }
    }
  }

  private java.util.Set<String> loadColumnNames(String tableName) throws Exception {
    var columns = new java.util.HashSet<String>();
    try (var conn = connection.openConnection();
        var rs = conn.getMetaData().getColumns(null, null, tableName, null)) {
      while (rs.next()) {
        columns.add(rs.getString("COLUMN_NAME").toLowerCase());
      }
    }
    return columns;
  }

  private boolean columnExists(String tableName, String columnName) throws Exception {
    return loadColumnNames(tableName).contains(columnName.toLowerCase());
  }

  private boolean indexExists(String indexName) throws Exception {
    try (var conn = connection.openConnection();
        var stmt = conn.createStatement();
        var rs =
            stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='" + indexName + "'")) {
      return rs.next();
    }
  }

  @Entity
  public static class TestTable {
    @Id(autoIncrement = true)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(nullable = false)
    private int value;
  }

  @Entity
  public static class IndexedTable {
    @Id(autoIncrement = true)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    @Index
    private String name;
  }

  @Entity
  public static class CustomIndexTable {
    @Id(autoIncrement = true)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    @Index(name = "my_custom_index")
    private String title;
  }
}
