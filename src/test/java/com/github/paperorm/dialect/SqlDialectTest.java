package com.github.paperorm.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.paperorm.TestEntity;
import com.github.paperorm.mapping.EntityMetadata;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlDialectTest {

  private final SqlDialect dialect = new StandardSqlDialect();
  private EntityMetadata metadata;

  @BeforeEach
  void setUp() {
    var scanner = new ReflectionEntityScanner();
    this.metadata = scanner.scan(TestEntity.class);
  }

  @Test
  void createTableIncludesAllColumns() {
    var sql = this.dialect.createTable(this.metadata);

    assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS \"test_entities\""));
    assertTrue(sql.contains("\"id\" INTEGER PRIMARY KEY AUTOINCREMENT"));
    assertTrue(sql.contains("\"name\" TEXT NOT NULL"));
    assertTrue(sql.contains("\"count\" INTEGER NOT NULL"));
    assertTrue(sql.contains("\"active\" INTEGER"));
  }

  @Test
  void insertSkipsAutoIncrementColumn() {
    var sql = this.dialect.insert(this.metadata);

    assertEquals(
        "INSERT INTO \"test_entities\" (\"name\", \"count\", \"active\") VALUES (?, ?, ?)", sql);
  }

  @Test
  void updateSetsColumnsAndFiltersById() {
    var sql = this.dialect.update(this.metadata);

    assertEquals(
        "UPDATE \"test_entities\" SET \"name\" = ?, \"count\" = ?, \"active\" = ? WHERE \"id\" = ?",
        sql);
  }

  @Test
  void deleteByIdFiltersById() {
    var sql = this.dialect.deleteById(this.metadata);

    assertEquals("DELETE FROM \"test_entities\" WHERE \"id\" = ?", sql);
  }

  @Test
  void selectByIdFiltersById() {
    var sql = this.dialect.selectById(this.metadata);

    assertEquals(
        "SELECT \"id\", \"name\", \"count\", \"active\" FROM \"test_entities\" WHERE \"id\" = ?",
        sql);
  }
}
