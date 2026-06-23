package com.github.paperorm.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.github.paperorm.OrmSession;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.database.SqliteDatabaseConnection;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdentityMapTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private OrmSession session;

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    session = new OrmSession(connection, null, true);
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  @Test
  void shouldCacheEntityWhenEnabled() {
    var map = new IdentityMap<>(session, TestItem.class, true);
    var entity = new TestItem(42L, "Cached");

    var result = map.cacheOrGet(42L, entity);
    assertSame(entity, result);

    var resolved = map.resolve(42L);
    assertSame(entity, resolved);
  }

  @Test
  void shouldNotCacheWhenSessionDisabled() {
    var disabledSession = new OrmSession(connection, null, false);
    var map = new IdentityMap<>(disabledSession, TestItem.class, false);
    var entity = new TestItem(1L, "NotCached");

    map.cacheOrGet(1L, entity);
    var resolved = map.resolve(1L);
    assertNull(resolved);
  }

  @Test
  void shouldReturnExistingOnSecondCache() {
    var map = new IdentityMap<>(session, TestItem.class, true);
    var first = new TestItem(10L, "First");
    var second = new TestItem(10L, "Second");

    var result1 = map.cacheOrGet(10L, first);
    assertSame(first, result1);

    var result2 = map.cacheOrGet(10L, second);
    assertSame(first, result2);
  }

  @Test
  void shouldEvictEntity() {
    var map = new IdentityMap<>(session, TestItem.class, true);
    var entity = new TestItem(7L, "EvictMe");

    map.register(7L, entity);
    assertNotNull(map.resolve(7L));

    map.evict(7L);
    assertNull(map.resolve(7L));
  }

  @Test
  void shouldClearAll() {
    var map = new IdentityMap<>(session, TestItem.class, true);
    map.register(1L, new TestItem(1L, "A"));
    map.register(2L, new TestItem(2L, "B"));

    map.clear();
    assertNull(map.resolve(1L));
    assertNull(map.resolve(2L));
  }

  @Test
  void shouldWorkWithoutSession() {
    var map = new IdentityMap<>(null, TestItem.class, true);
    var entity = new TestItem(99L, "Local");

    map.register(99L, entity);
    var resolved = map.resolve(99L);
    assertSame(entity, resolved);
  }

  @Test
  void shouldResolveNullForUnknownId() {
    var map = new IdentityMap<>(session, TestItem.class, true);
    assertNull(map.resolve(999L));
  }

  @Test
  void shouldCacheOrGetWithCacheDisabledWhenNoSession() {
    var map = new IdentityMap<>(null, TestItem.class, false);
    var entity = new TestItem(3L, "Disabled");

    var result = map.cacheOrGet(3L, entity);
    assertSame(entity, result);
    assertNull(map.resolve(3L));
  }

  @Entity
  private static final class TestItem {
    @Id private Long id;
    private String name;

    TestItem(Long id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
