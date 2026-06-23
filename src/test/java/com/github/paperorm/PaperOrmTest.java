package com.github.paperorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.Table;
import com.github.paperorm.exception.MappingException;
import com.github.paperorm.mapping.TypeConverter;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperOrmTest {

  @TempDir Path tempDir;

  private PaperOrm paperOrm;

  @BeforeEach
  void setUp() {
    paperOrm =
        PaperOrm.builder()
            .sqlite(tempDir.resolve("test.db"))
            .registerEntity(TestEntity.class)
            .autoCreateTables(true)
            .build();
  }

  @AfterEach
  void tearDown() {
    paperOrm.close();
  }

  @Test
  void shouldRetrieveRepository() {
    var repository = paperOrm.getRepository(TestEntity.class);
    assertNotNull(repository);

    var testEntity = new TestEntity(null, "BuilderTest", 5, true);
    repository.save(testEntity);
    assertNotNull(testEntity.getId());

    var found = repository.findById(testEntity.getId());
    assertTrue(found.isPresent());
    assertEquals("BuilderTest", found.get().getName());
  }

  @Test
  void shouldThrowWhenRetrievingUnregisteredEntity() {
    assertThrows(MappingException.class, () -> paperOrm.getRepository(String.class));
  }

  @Test
  void shouldWorkWithVirtualThreads() {
    var vThreadOrm =
        PaperOrm.builder()
            .sqlite(tempDir.resolve("vthreads.db"))
            .registerEntity(TestEntity.class)
            .useVirtualThreads()
            .autoCreateTables(true)
            .build();
    try {
      var repository = vThreadOrm.getRepository(TestEntity.class);
      var testEntity = new TestEntity(null, "VThreadTest", 10, true);
      repository.saveAsync(testEntity).join();

      var found = repository.findByIdAsync(testEntity.getId()).join();
      assertTrue(found.isPresent());
      assertEquals("VThreadTest", found.get().getName());
    } finally {
      vThreadOrm.close();
    }
  }

  @Test
  void shouldWorkWithCustomTypeConverter() {
    var converterOrm =
        PaperOrm.builder()
            .sqlite(tempDir.resolve("converter.db"))
            .registerConverter(new CustomDataConverter())
            .registerEntity(CustomDataEntity.class)
            .autoCreateTables(true)
            .build();
    try {
      var repository = converterOrm.getRepository(CustomDataEntity.class);

      var entity = new CustomDataEntity();
      entity.id = 1L;
      entity.data = new CustomData("Rank", "Admin");
      repository.save(entity);

      var found = repository.findById(1L);
      assertTrue(found.isPresent());
      assertNotNull(found.get().data);
      assertEquals("Rank", found.get().data.key);
      assertEquals("Admin", found.get().data.value);
    } finally {
      converterOrm.close();
    }
  }

  @Test
  void shouldReuseCachedInstancesToPreserveIdentity() {
    var cacheOrm =
        PaperOrm.builder()
            .sqlite(tempDir.resolve("cache.db"))
            .registerEntity(TestEntity.class)
            .useCache(true)
            .autoCreateTables(true)
            .build();
    try {
      var repository = cacheOrm.getRepository(TestEntity.class);

      var entity = new TestEntity(1L, "CacheTest", 10, true);
      repository.save(entity);

      var first = repository.findById(1L);
      var second = repository.findById(1L);

      assertTrue(first.isPresent());
      assertTrue(second.isPresent());

      // Assert that both point to the exact same instance in memory (Identity Map)
      assertTrue(first.get() == second.get());

      // Modify first
      first.get().setName("ModifiedInCache");
      assertEquals("ModifiedInCache", second.get().getName());

      // Invalidate cache
      repository.clearCache();

      var third = repository.findById(1L);
      assertTrue(third.isPresent());
      // After cache clear, it should be a new instance
      assertTrue(first.get() != third.get());
      assertEquals("CacheTest", third.get().getName()); // Read from DB, has old name
    } finally {
      cacheOrm.close();
    }
  }

  @Test
  void shouldPerformSessionScopedOperationsAndRollbackCleanly() {
    var reward = new TestEntity(10L, "TxTest", 100, true);

    try (var session = paperOrm.openSession()) {
      var repo = session.getRepository(TestEntity.class);
      try {
        session.runInTransaction(
            (com.github.paperorm.database.VoidTransactionCallback)
                conn -> {
                  repo.save(reward);
                  throw new RuntimeException("Rollback session");
                });
      } catch (RuntimeException ignored) {
      }

      var found = repo.findById(10L);
      assertTrue(
          found.isEmpty(),
          "Session cache should be cleared on rollback and database should not have the uncommitted entity");
    }
  }

  @Test
  void shouldWorkWithFluentQueryBuilder() {
    var repository = paperOrm.getRepository(TestEntity.class);

    var e1 = new TestEntity(null, "Match", 5, true);
    var e2 = new TestEntity(null, "Match", 15, true);
    var e3 = new TestEntity(null, "NoMatch", 2, null);

    repository.save(e1);
    repository.save(e2);
    repository.save(e3);

    // Basic select eq
    var matches = repository.select().where("name").eq("Match").list();
    assertEquals(2, matches.size());

    // Chain greaterThan and order
    var greater = repository.select().where("name").eq("Match").and("count").greaterThan(10).list();
    assertEquals(1, greater.size());
    assertEquals(e2.getId(), greater.getFirst().getId());

    // Ordering and Limit
    var ordered =
        repository.select().where("name").eq("Match").orderBy("count", "DESC").limit(1).list();
    assertEquals(1, ordered.size());
    assertEquals(e2.getId(), ordered.getFirst().getId());

    // Test isNull / isNotNull
    var nullActive = repository.select().where("active").isNull().list();
    assertEquals(1, nullActive.size());
    assertEquals(e3.getId(), nullActive.getFirst().getId());

    var nonNullActive = repository.select().where("active").isNotNull().list();
    assertEquals(2, nonNullActive.size());

    // Test IN operator
    var inList = repository.select().where("id").in(e1.getId(), e3.getId()).list();
    assertEquals(2, inList.size());
  }
}

@Entity
@Table(name = "custom_data_entities")
class CustomDataEntity {

  @Id Long id;

  @Column CustomData data;
}

class CustomData {
  String key;
  String value;

  CustomData() {}

  CustomData(String key, String value) {
    this.key = key;
    this.value = value;
  }
}

class CustomDataConverter implements TypeConverter<CustomData> {

  @Override
  public Class<CustomData> getType() {
    return CustomData.class;
  }

  @Override
  public void setParameter(PreparedStatement statement, int index, CustomData value)
      throws SQLException {
    if (value == null) {
      statement.setString(index, null);
    } else {
      statement.setString(index, value.key + ":" + value.value);
    }
  }

  @Override
  public CustomData readValue(ResultSet resultSet, String columnName) throws SQLException {
    var raw = resultSet.getString(columnName);
    if (raw == null) {
      return null;
    }
    var parts = raw.split(":");
    return new CustomData(parts[0], parts[1]);
  }
}
