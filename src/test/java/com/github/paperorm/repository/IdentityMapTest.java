package com.github.paperorm.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.github.paperorm.OrmContext;
import com.github.paperorm.OrmFactory;
import com.github.paperorm.OrmSession;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.dialect.SqliteDialect;
import com.github.paperorm.mapping.IdResolver;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeMapper;
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
    var typeMapper = new TypeMapper();
    var scanner = new ReflectionEntityScanner(typeMapper);
    var dialect = new SqliteDialect();
    var context =
        new OrmContext(
            connection,
            scanner,
            dialect,
            typeMapper,
            new IdResolver(),
            Runnable::run,
            true,
            false,
            null);
    var factory = new OrmFactory(context);
    session = new OrmSession(context, factory);
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  @Test
  void shouldCacheEntityWhenEnabled() {
    var map = IdentityMap.sessionScoped(session, TestItem.class);
    var entity = new TestItem(42L, "Cached");

    var result = map.cacheOrGet(42L, entity);
    assertSame(entity, result);

    var resolved = map.resolve(42L);
    assertSame(entity, resolved);
  }

  @Test
  void shouldNotCacheWhenSessionDisabled() {
    var typeMapper = new TypeMapper();
    var scanner = new ReflectionEntityScanner(typeMapper);
    var dialect = new SqliteDialect();
    var disabledContext =
        new OrmContext(
            connection,
            scanner,
            dialect,
            typeMapper,
            new IdResolver(),
            Runnable::run,
            false,
            false,
            null);
    var disabledFactory = new OrmFactory(disabledContext);
    var disabledSession = new OrmSession(disabledContext, disabledFactory);
    var map = IdentityMap.sessionScoped(disabledSession, TestItem.class);
    var entity = new TestItem(1L, "NotCached");

    map.cacheOrGet(1L, entity);
    var resolved = map.resolve(1L);
    assertNull(resolved);
  }

  @Test
  void shouldReturnExistingOnSecondCache() {
    var map = IdentityMap.sessionScoped(session, TestItem.class);
    var first = new TestItem(10L, "First");
    var second = new TestItem(10L, "Second");

    var result1 = map.cacheOrGet(10L, first);
    assertSame(first, result1);

    var result2 = map.cacheOrGet(10L, second);
    assertSame(first, result2);
  }

  @Test
  void shouldEvictEntity() {
    var map = IdentityMap.sessionScoped(session, TestItem.class);
    var entity = new TestItem(7L, "EvictMe");

    map.register(7L, entity);
    assertNotNull(map.resolve(7L));

    map.evict(7L);
    assertNull(map.resolve(7L));
  }

  @Test
  void shouldClearAll() {
    var map = IdentityMap.sessionScoped(session, TestItem.class);
    map.register(1L, new TestItem(1L, "A"));
    map.register(2L, new TestItem(2L, "B"));

    map.clear();
    assertNull(map.resolve(1L));
    assertNull(map.resolve(2L));
  }

  @Test
  void shouldWorkWithoutSession() {
    var map = IdentityMap.local(TestItem.class, true);
    var entity = new TestItem(99L, "Local");

    map.register(99L, entity);
    var resolved = map.resolve(99L);
    assertSame(entity, resolved);
  }

  @Test
  void shouldResolveNullForUnknownId() {
    var map = IdentityMap.sessionScoped(session, TestItem.class);
    assertNull(map.resolve(999L));
  }

  @Test
  void shouldCacheOrGetWithCacheDisabledWhenNoSession() {
    var map = IdentityMap.local(TestItem.class, false);
    var entity = new TestItem(3L, "Disabled");

    var result = map.cacheOrGet(3L, entity);
    assertSame(entity, result);
    assertNull(map.resolve(3L));
  }

  @Entity
  @lombok.AllArgsConstructor
  private static final class TestItem {
    @Id private Long id;
    private String name;
  }
}
