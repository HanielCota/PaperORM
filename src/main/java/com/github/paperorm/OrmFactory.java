package com.github.paperorm;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.dialect.StandardSqlDialect;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.repository.Repository;
import com.github.paperorm.repository.SqlRepository;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class OrmFactory {

  private final DatabaseConnection connection;
  private final EntityScanner scanner;
  private final SqlDialect dialect;
  private final TypeMapper typeMapper;
  private final Executor executor;
  private final boolean useCache;

  public OrmFactory(DatabaseConnection connection) {
    this(connection, ForkJoinPool.commonPool());
  }

  public OrmFactory(DatabaseConnection connection, Executor executor) {
    this(connection, executor, true);
  }

  public OrmFactory(DatabaseConnection connection, Executor executor, boolean useCache) {
    this.connection = connection;
    this.scanner = new ReflectionEntityScanner();
    this.dialect = new StandardSqlDialect();
    this.typeMapper = new TypeMapper();
    this.executor = executor;
    this.useCache = useCache;
  }

  public OrmFactory(
      DatabaseConnection connection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper) {
    this(connection, scanner, dialect, typeMapper, ForkJoinPool.commonPool());
  }

  public OrmFactory(
      DatabaseConnection connection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor) {
    this(connection, scanner, dialect, typeMapper, executor, true);
  }

  public OrmFactory(
      DatabaseConnection connection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor,
      boolean useCache) {
    this.connection = connection;
    this.scanner = scanner;
    this.dialect = dialect;
    this.typeMapper = typeMapper;
    this.executor = executor;
    this.useCache = useCache;
  }

  public <T> Repository<T> createRepository(Class<T> entityClass) {
    return new SqlRepository<>(
        entityClass, connection, scanner, dialect, typeMapper, executor, useCache);
  }

  public <T> Repository<T> createRepository(Class<T> entityClass, OrmSession session) {
    return new SqlRepository<>(entityClass, session, scanner, dialect, typeMapper, executor);
  }

  public Executor executor() {
    return this.executor;
  }
}
