package com.github.paperorm;

import static org.junit.jupiter.api.Assertions.*;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.database.TransactionCallback;
import com.github.paperorm.database.VoidTransactionCallback;
import com.github.paperorm.dialect.SqliteDialect;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrmSessionTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private OrmFactory factory;
  private OrmSession session;

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    var scanner = new ReflectionEntityScanner();
    var dialect = new SqliteDialect();
    var typeMapper = new TypeMapper();
    factory = new OrmFactory(connection, scanner, dialect, typeMapper, Runnable::run, true);
    session = new OrmSession(connection, factory, true);

    var repo = session.getRepository(SessionEntity.class);
    repo.ensureTable();
  }

  @AfterEach
  void tearDown() {
    session.close();
    connection.close();
  }

  @Test
  void shouldCreateRepository() {
    var repo = session.getRepository(SessionEntity.class);
    assertNotNull(repo);
  }

  @Test
  void shouldCacheRepositoryInstances() {
    var repo1 = session.getRepository(SessionEntity.class);
    var repo2 = session.getRepository(SessionEntity.class);
    assertSame(repo1, repo2);
  }

  @Test
  void shouldProvideConnection() {
    assertSame(connection, session.connection());
  }

  @Test
  void shouldExposeCacheSetting() {
    assertTrue(session.isUseCache());

    var disabledSession = new OrmSession(connection, factory, false);
    assertFalse(disabledSession.isUseCache());
  }

  @Test
  void shouldRejectClosedSession() {
    session.close();
    assertThrows(IllegalStateException.class, () -> session.getRepository(SessionEntity.class));
  }

  @Test
  void shouldClearCacheOnClose() {
    var repo = session.getRepository(SessionEntity.class);
    var entity = new SessionEntity(1L, "Cached");
    repo.save(entity);

    var found = repo.findById(1L);
    assertTrue(found.isPresent());

    session.close();
    var session2 = new OrmSession(connection, factory, true);
    var repo2 = session2.getRepository(SessionEntity.class);
    var found2 = repo2.findById(1L);
    assertTrue(found2.isPresent());
    session2.close();
  }

  @Test
  void shouldRunTransactionAndClearCache() {
    var repo = session.getRepository(SessionEntity.class);
    var entity = new SessionEntity(100L, "TxTest");
    repo.save(entity);

    session.runInTransaction(
        (TransactionCallback<Void>)
            conn -> {
              var stmt = conn.createStatement();
              stmt.executeUpdate(
                  "UPDATE \"session_entity\" SET \"name\" = 'TxUpdated' WHERE \"id\" = 100");
              return null;
            });

    var found = repo.findById(100L);
    assertTrue(found.isPresent());
    assertEquals("TxUpdated", found.get().name);
  }

  @Test
  void shouldRunVoidTransaction() {
    session.runInTransaction(
        (VoidTransactionCallback)
            conn -> {
              var stmt = conn.createStatement();
              stmt.executeUpdate(
                  "INSERT INTO \"session_entity\" (\"id\", \"name\") VALUES (99, 'VoidTx')");
            });

    var repo = session.getRepository(SessionEntity.class);
    var found = repo.findById(99L);
    assertTrue(found.isPresent());
    assertEquals("VoidTx", found.get().name);
  }

  @Test
  void shouldRunAsyncTransaction() throws Exception {
    var future =
        session.runInTransactionAsync(
            (TransactionCallback<String>)
                conn -> {
                  var stmt = conn.createStatement();
                  stmt.executeUpdate(
                      "INSERT INTO \"session_entity\" (\"id\", \"name\") VALUES (50, 'Async')");
                  return "done";
                });

    assertEquals("done", future.get());
    var repo = session.getRepository(SessionEntity.class);
    var found = repo.findById(50L);
    assertTrue(found.isPresent());
  }

  @Test
  void shouldRunAsyncVoidTransaction() throws Exception {
    var future =
        session.runInTransactionAsync(
            (VoidTransactionCallback)
                conn -> {
                  var stmt = conn.createStatement();
                  stmt.executeUpdate(
                      "INSERT INTO \"session_entity\" (\"id\", \"name\") VALUES (51, 'AsyncVoid')");
                });

    future.get();
    var repo = session.getRepository(SessionEntity.class);
    assertTrue(repo.findById(51L).isPresent());
  }

  @Test
  void shouldRegisterAndRetrieveIdentity() {
    var entity = new SessionEntity(1L, "Identity");
    session.registerIdentity(SessionEntity.class, 1L, entity);
    var cached = session.getIdentity(SessionEntity.class, 1L);
    assertSame(entity, cached);
  }

  @Test
  void shouldEvictIdentity() {
    var entity = new SessionEntity(2L, "EvictMe");
    session.registerIdentity(SessionEntity.class, 2L, entity);
    session.evictIdentity(SessionEntity.class, 2L);
    assertNull(session.getIdentity(SessionEntity.class, 2L));
  }

  @Test
  void shouldClearAllIdentities() {
    session.registerIdentity(SessionEntity.class, 1L, new SessionEntity(1L, "A"));
    session.registerIdentity(SessionEntity.class, 2L, new SessionEntity(2L, "B"));
    session.clearIdentityMap();
    assertNull(session.getIdentity(SessionEntity.class, 1L));
    assertNull(session.getIdentity(SessionEntity.class, 2L));
  }

  @Test
  void shouldRejectNullArgs() {
    assertThrows(NullPointerException.class, () -> session.getRepository(null));
    assertThrows(
        NullPointerException.class, () -> session.runInTransaction((TransactionCallback<?>) null));
    assertThrows(
        NullPointerException.class, () -> session.runInTransaction((VoidTransactionCallback) null));
  }

  @Test
  void shouldReturnNullForUnknownCache() {
    assertNull(session.getIdentity(SessionEntity.class, 999L));
  }

  @Entity
  public static class SessionEntity {
    @Id
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    public SessionEntity() {}

    public SessionEntity(Long id, String name) {
      this.id = id;
      this.name = name;
    }
  }
}
