package com.github.paperorm;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.repository.Repository;
import com.github.paperorm.repository.SqlRepository;
import com.github.paperorm.repository.SqlRepositoryConfig;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class OrmFactory {

  private final DatabaseConnection connection;
  private final EntityScanner scanner;
  private final SqlDialect dialect;
  private final TypeMapper typeMapper;
  private final Executor executor;
  private final boolean useCache;
  private final CompletableFuture<Void> migrationsFuture;

  public OrmFactory(
      DatabaseConnection connection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor,
      boolean useCache) {
    this(connection, scanner, dialect, typeMapper, executor, useCache, null);
  }

  public OrmFactory(
      DatabaseConnection connection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor,
      boolean useCache,
      CompletableFuture<Void> migrationsFuture) {
    this.connection = connection;
    this.scanner = scanner;
    this.dialect = dialect;
    this.typeMapper = typeMapper;
    this.executor = executor;
    this.useCache = useCache;
    this.migrationsFuture = migrationsFuture;
  }

  public <T> Repository<T> createRepository(Class<T> entityClass) {
    Objects.requireNonNull(entityClass, "entityClass");
    var config =
        new SqlRepositoryConfig(
            connection, scanner, dialect, typeMapper, executor, useCache, migrationsFuture);
    return new SqlRepository<>(entityClass, config);
  }

  public <T> Repository<T> createRepository(Class<T> entityClass, OrmSession session) {
    return new SqlRepository<>(
        entityClass, session, scanner, dialect, typeMapper, executor, migrationsFuture);
  }

  public Executor executor() {
    return this.executor;
  }
}
