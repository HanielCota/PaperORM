package com.github.paperorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            .connectionConfig(new ConnectionConfig.Sqlite(tempDir.resolve("test.db")))
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
            .connectionConfig(new ConnectionConfig.Sqlite(tempDir.resolve("vthreads.db")))
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
            .connectionConfig(new ConnectionConfig.Sqlite(tempDir.resolve("converter.db")))
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
  void shouldWorkWithCustomLogger() {
    var logger = java.util.logging.Logger.getLogger("TestLogger");
    var loggerOrm =
        PaperOrm.builder()
            .connectionConfig(new ConnectionConfig.Sqlite(tempDir.resolve("logger.db")))
            .logger(logger)
            .registerEntity(TestEntity.class)
            .autoCreateTables(true)
            .build();
    try {
      var repository = loggerOrm.getRepository(TestEntity.class);
      var testEntity = new TestEntity(null, "LoggerTest", 1, true);
      repository.save(testEntity);
      assertTrue(repository.findById(testEntity.getId()).isPresent());
    } finally {
      loggerOrm.close();
    }
  }

  @Test
  void shouldReuseCachedInstancesToPreserveIdentity() {
    var cacheOrm =
        PaperOrm.builder()
            .connectionConfig(new ConnectionConfig.Sqlite(tempDir.resolve("cache.db")))
            .registerEntity(TestEntity.class)
            .useCache(true)
            .autoCreateTables(true)
            .build();
    try {
      var repository = cacheOrm.getRepository(TestEntity.class);

      var entity = new TestEntity(null, "CacheTest", 10, true);
      repository.save(entity);
      var savedId = entity.getId();

      var first = repository.findById(savedId);
      var second = repository.findById(savedId);

      assertTrue(first.isPresent());
      assertTrue(second.isPresent());

      assertTrue(first.get() == second.get());

      first.get().setName("ModifiedInCache");
      assertEquals("ModifiedInCache", second.get().getName());

      repository.clearCache();

      var third = repository.findById(savedId);
      assertTrue(third.isPresent());
      assertTrue(first.get() != third.get());
      assertEquals("CacheTest", third.get().getName());
    } finally {
      cacheOrm.close();
    }
  }

  @Test
  void shouldPerformSessionScopedOperationsAndRollbackCleanly() {
    var reward = new TestEntity(null, "TxTest", 100, true);

    try (var session = paperOrm.openSession()) {
      var repo = session.getRepository(TestEntity.class);
      try {
        session.runInTransaction(
            conn -> {
              repo.save(reward);
              throw new RuntimeException("Rollback session");
            });
      } catch (RuntimeException ignored) {
        // Expected: forced rollback
      }

      var found = repo.findById(-1L);
      assertTrue(
          found.isEmpty(),
          "Session cache should be cleared on rollback and database should not have the uncommitted entity");
    }
  }

  @Test
  void shouldPerformSessionScopedOperationsAndRollbackCleanlyAsync() {
    var reward = new TestEntity(null, "TxTestAsync", 100, true);

    try (var session = paperOrm.openSession()) {
      var repo = session.getRepository(TestEntity.class);

      var future =
          session.runInTransactionAsync(
              conn -> {
                repo.save(reward);
                throw new RuntimeException("Rollback session async");
              });

      assertThrows(Exception.class, future::join);

      var found = repo.findById(-1L);
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

    var matches = repository.select().where("name").eq("Match").list();
    assertEquals(2, matches.size());

    var greater = repository.select().where("name").eq("Match").and("count").greaterThan(10).list();
    assertEquals(1, greater.size());
    assertEquals(e2.getId(), greater.getFirst().getId());

    var ordered =
        repository.select().where("name").eq("Match").orderBy("count", "DESC").limit(1).list();
    assertEquals(1, ordered.size());
    assertEquals(e2.getId(), ordered.getFirst().getId());

    var nullActive = repository.select().where("active").isNull().list();
    assertEquals(1, nullActive.size());
    assertEquals(e3.getId(), nullActive.getFirst().getId());

    var nonNullActive = repository.select().where("active").isNotNull().list();
    assertEquals(2, nonNullActive.size());

    var inList = repository.select().where("id").in(e1.getId(), e3.getId()).list();
    assertEquals(2, inList.size());
  }

  @Test
  void shouldWorkWithStartersAndGsonJsonConverterAndAbstractRepository() {
    var dbPath = tempDir.resolve("starter.db");

    var orm =
        PaperOrm.builder()
            .connectionConfig(new ConnectionConfig.Sqlite(dbPath))
            .registerJsonConverter(CustomInfo.class)
            .registerEntity(CustomRepoEntity.class)
            .autoCreateTables(true)
            .build();

    try {
      var customRepo = new CustomEntityRepository(orm);
      customRepo.ensureTable();

      var entity = new CustomRepoEntity();
      entity.info = new CustomInfo("GsonIsAwesome", 42);
      customRepo.save(entity);

      var found = customRepo.findById(entity.id);
      assertTrue(found.isPresent());
      assertEquals("GsonIsAwesome", found.get().info.text);
      assertEquals(42, found.get().info.code);
    } finally {
      orm.close();
    }
  }

  @Test
  void shouldApplyMigrationsFromDirectory() throws java.io.IOException {
    var migrationsDir = tempDir.resolve("migrations");
    java.nio.file.Files.createDirectories(migrationsDir);

    var v1 = migrationsDir.resolve("V1.sql");
    java.nio.file.Files.writeString(
        v1,
        """
        CREATE TABLE players_migrated (
            id INTEGER PRIMARY KEY,
            username TEXT NOT NULL
        );
        """);

    var v2 = migrationsDir.resolve("V2.sql");
    java.nio.file.Files.writeString(
        v2,
        """
        ALTER TABLE players_migrated ADD COLUMN points INTEGER DEFAULT 0;
        """);

    var loadedMigrations =
        com.github.paperorm.migration.MigrationRunner.loadFromDirectory(migrationsDir);
    assertEquals(2, loadedMigrations.size());
    assertEquals(1, loadedMigrations.get(0).version());
    assertEquals(2, loadedMigrations.get(1).version());

    var dbPath = tempDir.resolve("migration_test.db");
    var orm =
        PaperOrm.builder()
            .connectionConfig(new ConnectionConfig.Sqlite(dbPath))
            .migrations(loadedMigrations)
            .build();

    try {
      orm.awaitMigrations();
      try (var conn = orm.connection().openConnection();
          var stmt = conn.createStatement();
          var rs = stmt.executeQuery("SELECT points FROM players_migrated")) {
        assertFalse(rs.next());
      } catch (SQLException e) {
        throw new RuntimeException("Migration failed", e);
      }
    } finally {
      orm.close();
    }
  }
}

@Entity
@Table(name = "custom_data_entities")
class CustomDataEntity {

  @Id Long id;

  @Column CustomData data;
}

@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
class CustomData {
  String key;
  String value;
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

@Entity
@Table(name = "custom_repos")
class CustomRepoEntity {
  @Id(autoIncrement = true)
  Long id;

  @Column CustomInfo info;
}

@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
class CustomInfo {
  String text;
  int code;
}

class CustomEntityRepository
    extends com.github.paperorm.repository.AbstractRepository<CustomRepoEntity> {
  CustomEntityRepository(PaperOrm orm) {
    super(CustomRepoEntity.class, orm);
  }
}
