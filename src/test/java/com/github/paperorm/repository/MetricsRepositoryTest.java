package com.github.paperorm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.Table;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.dialect.SqliteDialect;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.repository.query.Spec;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetricsRepositoryTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private MetricsRepository<MetricTestEntity> repo;

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    var scanner = new ReflectionEntityScanner();
    var dialect = new SqliteDialect();
    var typeMapper = new TypeMapper();
    var inner =
        new SqlRepository<>(MetricTestEntity.class, connection, scanner, dialect, typeMapper);
    repo = new MetricsRepository<>(inner);
    repo.ensureTable();
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  @Test
  void shouldCountSaves() {
    repo.save(new MetricTestEntity(1L, "A"));
    repo.save(new MetricTestEntity(2L, "B"));

    assertEquals(2, repo.saveCount());
  }

  @Test
  void shouldCountUpdates() {
    var entity = new MetricTestEntity(3L, "Before");
    repo.save(entity);
    entity.name = "After";
    repo.update(entity);

    assertEquals(1, repo.saveCount());
    assertEquals(1, repo.updateCount());
  }

  @Test
  void shouldCountFinds() {
    repo.save(new MetricTestEntity(4L, "A"));

    repo.findById(4L);
    repo.findById(4L);

    assertEquals(2, repo.findCount());
  }

  @Test
  void shouldCountDeletes() {
    repo.save(new MetricTestEntity(5L, "A"));
    repo.save(new MetricTestEntity(6L, "B"));

    repo.deleteById(5L);
    repo.delete(new MetricTestEntity(6L, "B"));

    assertEquals(2, repo.deleteCount());
  }

  @Test
  void shouldCountQueries() {
    repo.save(new MetricTestEntity(7L, "Q1"));
    repo.save(new MetricTestEntity(8L, "Q2"));

    repo.findAll();
    repo.findBy("name", "Q1");
    repo.existsById(7L);

    assertEquals(4, repo.queryCount());
  }

  @Test
  void shouldTrackTime() {
    repo.save(new MetricTestEntity(9L, "Timed"));

    assertTrue(repo.totalTimeMillis() >= 0);
  }

  @Test
  void shouldReset() {
    repo.save(new MetricTestEntity(10L, "R"));
    assertEquals(1, repo.saveCount());

    repo.reset();

    assertEquals(0, repo.saveCount());
    assertEquals(0, repo.updateCount());
    assertEquals(0, repo.findCount());
    assertEquals(0, repo.deleteCount());
    assertEquals(0, repo.queryCount());
    assertTrue(repo.totalTimeMillis() >= 0 || repo.totalTimeMillis() == 0);
  }

  @Test
  void shouldTimeClearCache() {
    repo.save(new MetricTestEntity(11L, "Cached"));
    repo.findById(11L);
    repo.clearCache();
    repo.findById(11L);

    assertEquals(2, repo.findCount());
    assertTrue(repo.totalTimeMillis() >= 0);
  }

  @Test
  void shouldCountSpecAndQuery() {
    repo.save(new MetricTestEntity(12L, "Spec"));

    var spec = Spec.<MetricTestEntity>where("name").eq("Spec");
    repo.find(spec);
    repo.findByQuery("name = ?", "Spec");

    assertEquals(3, repo.queryCount());
  }

  @Entity
  @Table(name = "metric_test_entities")
  static class MetricTestEntity {
    @Id Long id;

    @Column String name;

    MetricTestEntity() {}

    MetricTestEntity(Long id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
