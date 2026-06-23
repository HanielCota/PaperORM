package com.github.paperorm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.Table;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.dialect.StandardSqlDialect;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.repository.query.Spec;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoggingRepositoryTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private SqlRepository<LogTestEntity> inner;
  private LoggingRepository<LogTestEntity> repo;
  private final Logger logger = Logger.getLogger("TestLogger");

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    var scanner = new ReflectionEntityScanner();
    var dialect = new StandardSqlDialect();
    var typeMapper = new TypeMapper();
    inner = new SqlRepository<>(LogTestEntity.class, connection, scanner, dialect, typeMapper);
    repo = new LoggingRepository<>(inner, logger);
    repo.ensureTable();
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  @Test
  void shouldLogAndProxySave() {
    var entity = new LogTestEntity(1L, "LogTest");
    repo.save(entity);

    var found = repo.findById(1L);
    assertTrue(found.isPresent());
    assertEquals("LogTest", found.get().name);
  }

  @Test
  void shouldLogAndProxyUpdate() {
    var entity = new LogTestEntity(2L, "Before");
    repo.save(entity);

    entity.name = "After";
    repo.update(entity);

    var found = repo.findById(2L);
    assertTrue(found.isPresent());
    assertEquals("After", found.get().name);
  }

  @Test
  void shouldLogAndProxyDelete() {
    var entity = new LogTestEntity(3L, "ToDelete");
    repo.save(entity);
    repo.delete(entity);

    var found = repo.findById(3L);
    assertTrue(found.isEmpty());
  }

  @Test
  void shouldLogAndProxyFindAll() {
    repo.save(new LogTestEntity(4L, "A"));
    repo.save(new LogTestEntity(5L, "B"));

    var all = repo.findAll();
    assertEquals(2, all.size());
  }

  @Test
  void shouldLogAndProxyFindBy() {
    repo.save(new LogTestEntity(6L, "Unique"));

    var found = repo.findBy("name", "Unique");
    assertEquals(1, found.size());
  }

  @Test
  void shouldLogAndProxyExistsById() {
    repo.save(new LogTestEntity(7L, "Exists"));

    assertTrue(repo.existsById(7L));
    assertFalse(repo.existsById(999L));
  }

  @Test
  void shouldLogAndProxyAsyncOperations() {
    var entity = new LogTestEntity(8L, "AsyncLog");
    repo.saveAsync(entity).join();

    var exists = repo.existsByIdAsync(8L).join();
    assertTrue(exists);

    var found = repo.findByIdAsync(8L).join();
    assertTrue(found.isPresent());
    assertEquals("AsyncLog", found.get().name);

    repo.deleteByIdAsync(8L).join();
    assertFalse(repo.existsByIdAsync(8L).join());
  }

  @Test
  void shouldLogAndProxyClearCache() {
    var entity = new LogTestEntity(9L, "Cached");
    repo.save(entity);

    var first = repo.findById(9L);
    assertTrue(first.isPresent());

    repo.clearCache();

    var second = repo.findById(9L);
    assertTrue(second.isPresent());
  }

  @Test
  void shouldLogAndProxySelectQuery() {
    var entity = new LogTestEntity(10L, "QueryTest");
    repo.save(entity);

    var result = repo.select().where("name").eq("QueryTest").list();
    assertEquals(1, result.size());
  }

  @Test
  void shouldLogAndProxySpec() {
    repo.save(new LogTestEntity(11L, "SpecTest"));

    var spec = Spec.<LogTestEntity>where("name").eq("SpecTest");
    var result = repo.find(spec);
    assertEquals(1, result.size());
  }

  @Test
  void shouldLogAndProxyFindByQuery() {
    repo.save(new LogTestEntity(12L, "RawQuery"));

    var result = repo.findByQuery("name = ?", "RawQuery");
    assertEquals(1, result.size());
  }

  @Entity
  @Table(name = "log_test_entities")
  static class LogTestEntity {
    @Id Long id;

    @Column String name;

    LogTestEntity() {}

    LogTestEntity(Long id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
