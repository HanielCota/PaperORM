package com.github.paperorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Disabled("Requires Docker for Testcontainers")
public class MysqlIntegrationTest {

  @Container
  private static final MySQLContainer<?> mysqlContainer =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("paperorm_test")
          .withUsername("test")
          .withPassword("test");

  private static PaperOrm orm;

  @BeforeAll
  static void setUp() {
    orm =
        PaperOrm.builder()
            .connectionConfig(
                new ConnectionConfig.MySql(
                    mysqlContainer.getHost(),
                    mysqlContainer.getMappedPort(3306),
                    mysqlContainer.getDatabaseName(),
                    mysqlContainer.getUsername(),
                    mysqlContainer.getPassword()))
            .autoCreateTables(true)
            .registerEntity(TestEntity.class)
            .build();
  }

  @AfterAll
  static void tearDown() {
    if (orm != null) {
      orm.close();
    }
  }

  @Test
  void testBasicCrudOperationsInMysql() {
    var repository = orm.getRepository(TestEntity.class);

    var entity = new TestEntity();
    entity.setName("MySQL Test");
    entity.setCount(42);

    // Create
    repository.save(entity);
    assertNotNull(entity.getId());

    // Read
    Optional<TestEntity> found = repository.findById(entity.getId());
    assertTrue(found.isPresent());
    assertEquals("MySQL Test", found.get().getName());
    assertEquals(42, found.get().getCount());

    // Update
    found.get().setCount(43);
    repository.save(found.get());

    var updated = repository.findById(entity.getId()).get();
    assertEquals(43, updated.getCount());

    // Delete
    repository.deleteById(updated.getId());
    assertTrue(repository.findById(entity.getId()).isEmpty());
  }
}
