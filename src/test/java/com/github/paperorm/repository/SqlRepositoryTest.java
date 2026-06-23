package com.github.paperorm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.ManyToOne;
import com.github.paperorm.annotation.PostLoad;
import com.github.paperorm.annotation.PreDelete;
import com.github.paperorm.annotation.PrePersist;
import com.github.paperorm.annotation.PreUpdate;
import com.github.paperorm.annotation.Table;
import com.github.paperorm.annotation.Transient;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.database.VoidTransactionCallback;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.dialect.SqliteDialect;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlRepositoryTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private SqlRepository<TestReward> repository;
  private EntityScanner scanner;
  private SqlDialect dialect;
  private TypeMapper typeMapper;

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    scanner = new ReflectionEntityScanner();
    dialect = new SqliteDialect();
    typeMapper = new TypeMapper();
    repository = new SqlRepository<>(TestReward.class, connection, scanner, dialect, typeMapper);
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  @Test
  void shouldCreateTable() {
    repository.ensureTable();
  }

  @Test
  void shouldSaveAndFindById() {
    repository.ensureTable();

    var reward = new TestReward(1L, "Daily", 100.0);
    repository.save(reward);

    var found = repository.findById(1L);

    assertTrue(found.isPresent());
    assertEquals("Daily", found.get().name);
    assertEquals(100.0, found.get().amount, 0.001);
  }

  @Test
  void shouldUpdateEntity() {
    repository.ensureTable();

    var reward = new TestReward(2L, "Weekly", 500.0);
    repository.save(reward);

    reward.name = "Monthly";
    reward.amount = 1000.0;
    repository.update(reward);

    var found = repository.findById(2L);

    assertTrue(found.isPresent());
    assertEquals("Monthly", found.get().name);
    assertEquals(1000.0, found.get().amount, 0.001);
  }

  @Test
  void shouldDeleteById() {
    repository.ensureTable();

    var reward = new TestReward(3L, "OneTime", 50.0);
    repository.save(reward);

    repository.deleteById(3L);

    var found = repository.findById(3L);

    assertTrue(found.isEmpty());
  }

  @Test
  void shouldFindAll() {
    repository.ensureTable();

    repository.save(new TestReward(4L, "A", 10.0));
    repository.save(new TestReward(5L, "B", 20.0));

    var all = repository.findAll();

    assertEquals(2, all.size());
  }

  @Test
  void shouldFindByColumn() {
    repository.ensureTable();

    repository.save(new TestReward(6L, "Findable", 30.0));
    repository.save(new TestReward(7L, "Other", 40.0));

    var found = repository.findBy("name", "Findable");

    assertEquals(1, found.size());
    assertEquals("Findable", found.getFirst().name);
  }

  @Test
  void shouldCheckExistenceById() {
    repository.ensureTable();

    repository.save(new TestReward(8L, "Existing", 60.0));

    assertTrue(repository.existsById(8L));
    assertFalse(repository.existsById(999L));
  }

  @Test
  void shouldGenerateAutoIncrementId() {
    var autoRepository =
        new SqlRepository<>(AutoReward.class, connection, scanner, dialect, typeMapper);
    autoRepository.ensureTable();

    var reward = new AutoReward();
    reward.name = "Generated";
    reward.amount = 75.0;

    autoRepository.save(reward);

    assertNotNull(reward.id);
    assertTrue(reward.id > 0);

    var found = autoRepository.findById(reward.id);
    assertTrue(found.isPresent());
    assertEquals("Generated", found.get().name);
  }

  @Test
  void shouldIgnoreTransientField() {
    repository.ensureTable();

    var reward = new TestReward(9L, "WithTransient", 80.0);
    reward.temporaryCache = "should not be persisted";
    repository.save(reward);

    repository.clearCache();

    var found = repository.findById(9L);

    assertTrue(found.isPresent());
    assertEquals("default", found.get().temporaryCache);
  }

  @Test
  void shouldRollbackTransactionOnFailure() {
    repository.ensureTable();

    var reward = new TestReward(10L, "TxReward", 100.0);

    try {
      connection.runInTransaction(
          (VoidTransactionCallback)
              txConnection -> {
                repository.save(reward);
                throw new RuntimeException("Rollback transaction");
              });
    } catch (RuntimeException ignored) {
      // Expected: forced rollback
    }

    repository.clearCache();

    var found = repository.findById(10L);
    assertTrue(
        found.isEmpty(), "Reward should not have been saved because transaction was rolled back");
  }

  @Test
  void shouldPerformAsyncOperations() throws Exception {
    repository.ensureTableAsync().join();

    var reward = new TestReward(11L, "AsyncReward", 150.0);
    repository.saveAsync(reward).join();

    var exists = repository.existsByIdAsync(11L).join();
    assertTrue(exists);

    var foundOpt = repository.findByIdAsync(11L).join();
    assertTrue(foundOpt.isPresent());
    assertEquals("AsyncReward", foundOpt.get().name);

    reward.name = "AsyncUpdated";
    repository.updateAsync(reward).join();

    var updatedOpt = repository.findByIdAsync(11L).join();
    assertTrue(updatedOpt.isPresent());
    assertEquals("AsyncUpdated", updatedOpt.get().name);

    var list = repository.findAllAsync().join();
    assertEquals(1, list.size());

    var filtered = repository.findByAsync("name", "AsyncUpdated").join();
    assertEquals(1, filtered.size());

    repository.deleteByIdAsync(11L).join();
    var existsAfter = repository.existsByIdAsync(11L).join();
    assertFalse(existsAfter);
  }

  @Test
  void shouldMapRelationshipAndRunCustomQueries() {
    var txRepository =
        new SqlRepository<>(TestTransaction.class, connection, scanner, dialect, typeMapper);
    repository.ensureTable();
    txRepository.ensureTable();

    var reward = new TestReward(100L, "BossDrop", 250.0);
    repository.save(reward);

    var tx1 = new TestTransaction(null, "DEPOSIT", reward);
    var tx2 = new TestTransaction(null, "WITHDRAW", reward);
    txRepository.save(tx1);
    txRepository.save(tx2);

    assertNotNull(tx1.id);
    assertNotNull(tx2.id);

    // Test findById relationship mapping (referenced shell)
    var foundTxOpt = txRepository.findById(tx1.id);
    assertTrue(foundTxOpt.isPresent());
    var foundTx = foundTxOpt.get();
    assertEquals("DEPOSIT", foundTx.type);
    assertNotNull(foundTx.reward);
    assertEquals(100L, foundTx.reward.id);

    // Test findByQuery / custom queries
    var deposits = txRepository.findByQuery("type = ? AND reward_id = ?", "DEPOSIT", 100L);
    assertEquals(1, deposits.size());
    assertEquals(tx1.id, deposits.getFirst().id);
  }

  @Test
  void shouldAutoMigrateNewColumns() throws Exception {
    // 1. Create table manually with missing columns
    try (var conn = connection.openConnection();
        var stmt = conn.createStatement()) {
      stmt.executeUpdate("CREATE TABLE auto_migrate_entities (id INTEGER PRIMARY KEY, name TEXT)");
    }

    // 2. Instantiate repository for entity that has new fields
    var migrateRepository =
        new SqlRepository<>(AutoMigrateEntity.class, connection, scanner, dialect, typeMapper);

    // 3. ensureTable should alter the table to add newColumn
    migrateRepository.ensureTable();

    // 4. Test that we can save and retrieve data using the new column
    var entity = new AutoMigrateEntity(1L, "MigrationTest", "SomeNewValue");
    migrateRepository.save(entity);

    var found = migrateRepository.findById(1L);
    assertTrue(found.isPresent());
    assertEquals("MigrationTest", found.get().name);
    assertEquals("SomeNewValue", found.get().newColumn);
  }

  @Test
  void shouldApplyUniqueAndIndexConstraints() {
    var indexedRepo =
        new SqlRepository<>(IndexedEntity.class, connection, scanner, dialect, typeMapper);
    indexedRepo.ensureTable();

    // 1. Save first profile
    indexedRepo.save(new IndexedEntity(1L, "uuid-1"));

    // 2. Save second profile with duplicate UUID (should fail because of UNIQUE)
    assertThrows(Exception.class, () -> indexedRepo.save(new IndexedEntity(2L, "uuid-1")));
  }

  @Test
  void shouldWorkWithOrderByWithoutWhere() {
    repository.ensureTable();
    repository.save(new TestReward(1L, "Alpha", 10.0));
    repository.save(new TestReward(2L, "Beta", 20.0));

    var result = repository.select().orderBy("name", "ASC").list();
    assertEquals(2, result.size());
    assertEquals("Alpha", result.get(0).name);
    assertEquals("Beta", result.get(1).name);
  }

  @Test
  void shouldWorkWithLimitWithoutWhere() {
    repository.ensureTable();
    repository.save(new TestReward(1L, "A", 1.0));
    repository.save(new TestReward(2L, "B", 2.0));

    var result = repository.select().limit(1).list();
    assertEquals(1, result.size());
  }

  @Test
  void shouldFireLifecycleCallbacks() {
    var cbRepo =
        new SqlRepository<>(CallbackEntity.class, connection, scanner, dialect, typeMapper);
    cbRepo.ensureTable();

    var entity = new CallbackEntity();
    entity.name = "CallbackTest";
    cbRepo.save(entity);

    assertTrue(entity.prePersistFired, "@PrePersist should have fired on save");
    assertEquals("callback_name", entity.name, "@PrePersist should have modified name");

    entity.name = "Updated";
    entity.preUpdateFired = false;
    cbRepo.update(entity);
    assertTrue(entity.preUpdateFired, "@PreUpdate should have fired on update");

    cbRepo.clearCache();
    var loaded = cbRepo.findById(entity.id);
    assertTrue(loaded.isPresent());
    assertTrue(loaded.get().postLoadFired, "@PostLoad should have fired on findById");

    cbRepo.delete(entity);
    assertTrue(entity.preDeleteFired, "@PreDelete should have fired on delete");
  }

  @Test
  void shouldWorkWithEnumField() {
    var enumRepo = new SqlRepository<>(EnumEntity.class, connection, scanner, dialect, typeMapper);
    enumRepo.ensureTable();

    var entity = new EnumEntity();
    entity.name = "EnumTest";
    entity.status = Status.ACTIVE;
    enumRepo.save(entity);

    var found = enumRepo.findById(entity.id);
    assertTrue(found.isPresent());
    assertEquals(Status.ACTIVE, found.get().status);

    found.get().status = Status.INACTIVE;
    enumRepo.update(found.get());

    var updated = enumRepo.findById(entity.id);
    assertTrue(updated.isPresent());
    assertEquals(Status.INACTIVE, updated.get().status);
  }

  @Test
  void shouldQueryWithNotEqAndLessThan() {
    repository.ensureTable();
    repository.save(new TestReward(1L, "A", 10.0));
    repository.save(new TestReward(2L, "B", 20.0));
    repository.save(new TestReward(3L, "C", 30.0));

    var notEq = repository.select().where("amount").notEq(20.0).list();
    assertEquals(2, notEq.size());

    var lessThan = repository.select().where("amount").lessThan(25.0).list();
    assertEquals(2, lessThan.size());
  }

  @Test
  void shouldRejectNullArgs() {
    repository.ensureTable();
    assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    assertThrows(IllegalArgumentException.class, () -> repository.findById(null));
    assertThrows(IllegalArgumentException.class, () -> repository.deleteById(null));
  }

  @Test
  void shouldBatchSaveWithManualIds() {
    repository.ensureTable();
    var entities =
        java.util.List.of(
            new TestReward(50L, "Batch1", 10.0),
            new TestReward(51L, "Batch2", 20.0),
            new TestReward(52L, "Batch3", 30.0));

    repository.saveAll(entities);

    assertEquals(3, repository.findAll().size());
    assertTrue(repository.findById(50L).isPresent());
    assertTrue(repository.findById(51L).isPresent());
    assertTrue(repository.findById(52L).isPresent());
  }

  @Test
  void shouldBatchSaveWithAutoIncrement() {
    var autoRepo = new SqlRepository<>(AutoReward.class, connection, scanner, dialect, typeMapper);
    autoRepo.ensureTable();

    var a = new AutoReward();
    a.name = "BatchA";
    a.amount = 1.0;
    var b = new AutoReward();
    b.name = "BatchB";
    b.amount = 2.0;

    autoRepo.saveAll(java.util.List.of(a, b));

    assertNotNull(a.id);
    assertNotNull(b.id);
    assertTrue(a.id > 0);
    assertTrue(b.id > 0);
    assertEquals(2, autoRepo.findAll().size());
  }

  @Test
  void shouldBatchUpdate() {
    repository.ensureTable();
    var entities =
        java.util.List.of(new TestReward(60L, "Upd1", 1.0), new TestReward(61L, "Upd2", 2.0));
    repository.saveAll(entities);

    entities.get(0).name = "Updated1";
    entities.get(1).name = "Updated2";
    repository.updateAll(entities);

    assertEquals("Updated1", repository.findById(60L).get().name);
    assertEquals("Updated2", repository.findById(61L).get().name);
  }

  @Test
  void shouldCountWithQuery() {
    repository.ensureTable();
    repository.save(new TestReward(70L, "Count1", 1.0));
    repository.save(new TestReward(71L, "Count1", 2.0));
    repository.save(new TestReward(72L, "Count2", 3.0));

    var total = repository.select().count();
    assertEquals(3, total);

    var filtered = repository.select().where("name").eq("Count1").count();
    assertEquals(2, filtered);

    var zero = repository.select().where("name").eq("Nonexistent").count();
    assertEquals(0, zero);
  }

  @Test
  void shouldCountByQuery() {
    repository.ensureTable();
    repository.save(new TestReward(80L, "CQ1", 10.0));
    repository.save(new TestReward(81L, "CQ2", 20.0));

    var count = repository.countByQuery("name = ?", "CQ1");
    assertEquals(1, count);
  }

  @Test
  void shouldHandleConcurrentAccess() throws Exception {
    repository.ensureTable();

    var threads = new java.util.ArrayList<Thread>();
    var errors = new java.util.concurrent.atomic.AtomicInteger(0);

    for (int i = 0; i < 10; i++) {
      var id = 90L + i;
      var reward = new TestReward(id, "Concurrent" + i, i * 10.0);
      var thread =
          new Thread(
              () -> {
                try {
                  repository.save(reward);
                  var found = repository.findById(id);
                  if (found.isEmpty()) {
                    errors.incrementAndGet();
                  }
                } catch (Exception e) {
                  errors.incrementAndGet();
                }
              });
      threads.add(thread);
    }

    for (var t : threads) {
      t.start();
    }
    for (var t : threads) {
      t.join();
    }

    assertEquals(0, errors.get());
    assertEquals(10, repository.findAll().size());
  }
}

@Entity
@Table(name = "callback_entities")
class CallbackEntity {
  @Id(autoIncrement = true)
  Long id;

  @Column String name;
  boolean prePersistFired = false;
  boolean preUpdateFired = false;
  boolean postLoadFired = false;
  boolean preDeleteFired = false;

  CallbackEntity() {}

  @PrePersist
  void onPrePersist() {
    this.prePersistFired = true;
    this.name = "callback_name";
  }

  @PreUpdate
  void onPreUpdate() {
    this.preUpdateFired = true;
  }

  @PostLoad
  void onPostLoad() {
    this.postLoadFired = true;
  }

  @PreDelete
  void onPreDelete() {
    this.preDeleteFired = true;
  }
}

enum Status {
  ACTIVE,
  INACTIVE
}

@Entity
@Table(name = "enum_entities")
class EnumEntity {
  @Id(autoIncrement = true)
  Long id;

  @Column String name;

  @Column Status status;

  EnumEntity() {}
}

@Entity
@Table(name = "indexed_entities")
class IndexedEntity {

  @Id Long id;

  @Column(unique = true)
  @com.github.paperorm.annotation.Index
  String uuid;

  IndexedEntity() {}

  IndexedEntity(Long id, String uuid) {
    this.id = id;
    this.uuid = uuid;
  }
}

@Entity
@Table(name = "test_rewards")
class TestReward {

  @Id Long id;

  @Column(nullable = false)
  String name;

  @Column(nullable = false)
  double amount;

  @Transient String temporaryCache = "default";

  // Required by EntityMapper for reflection-based instantiation
  TestReward() {}

  TestReward(Long id, String name, double amount) {
    this.id = id;
    this.name = name;
    this.amount = amount;
  }
}

@Entity
@Table(name = "auto_rewards")
class AutoReward {

  @Id(autoIncrement = true)
  Long id;

  @Column(nullable = false)
  String name;

  @Column(nullable = false)
  double amount;

  // Required by EntityMapper for reflection-based instantiation
  AutoReward() {}
}

@Entity
@Table(name = "test_transactions")
class TestTransaction {

  @Id(autoIncrement = true)
  Long id;

  @Column(nullable = false)
  String type;

  @ManyToOne TestReward reward;

  TestTransaction() {}

  TestTransaction(Long id, String type, TestReward reward) {
    this.id = id;
    this.type = type;
    this.reward = reward;
  }
}

@Entity
@Table(name = "auto_migrate_entities")
class AutoMigrateEntity {

  @Id Long id;

  @Column String name;

  @Column String newColumn;

  AutoMigrateEntity() {}

  AutoMigrateEntity(Long id, String name, String newColumn) {
    this.id = id;
    this.name = name;
    this.newColumn = newColumn;
  }
}
