package com.github.paperorm;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.dialect.StandardSqlDialect;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.repository.Repository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class PaperOrm implements AutoCloseable {

  private final DatabaseConnection connection;
  private final OrmFactory factory;
  private final OrmSession defaultSession;

  private PaperOrm(
      DatabaseConnection connection,
      OrmFactory factory,
      List<Class<?>> registeredEntities,
      boolean autoCreateTables,
      boolean useCache) {
    this.connection = connection;
    this.factory = factory;
    this.defaultSession = new OrmSession(connection, factory, useCache);

    for (var entityClass : registeredEntities) {
      var repo = this.defaultSession.getRepository(entityClass);
      if (autoCreateTables) {
        repo.ensureTable();
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public <T> Repository<T> getRepository(Class<T> entityClass) {
    return this.defaultSession.getRepository(entityClass);
  }

  public DatabaseConnection connection() {
    return this.connection;
  }

  public OrmSession openSession() {
    return new OrmSession(this.connection, this.factory, this.defaultSession.isUseCache());
  }

  @Override
  public void close() {
    this.defaultSession.close();
    this.connection.close();
  }

  public static final class Builder {

    private Path sqlitePath;
    private DatabaseConnection connection;
    private Executor executor = ForkJoinPool.commonPool();
    private EntityScanner scanner = new ReflectionEntityScanner();
    private SqlDialect dialect = new StandardSqlDialect();
    private TypeMapper typeMapper = new TypeMapper();
    private final List<Class<?>> registeredEntities = new ArrayList<>();
    private boolean autoCreateTables = false;
    private boolean useCache = true;

    public Builder useCache(boolean useCache) {
      this.useCache = useCache;
      return this;
    }

    public Builder sqlite(Path path) {
      this.sqlitePath = path;
      return this;
    }

    public Builder connection(DatabaseConnection connection) {
      this.connection = connection;
      return this;
    }

    public Builder mysql(String host, int port, String database, String username, String password) {
      var config = new com.zaxxer.hikari.HikariConfig();
      config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
      config.setUsername(username);
      config.setPassword(password);
      config.setMaximumPoolSize(10);
      config.addDataSourceProperty("cachePrepStmts", "true");
      config.addDataSourceProperty("prepStmtCacheSize", "250");
      config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
      config.addDataSourceProperty("useServerPrepStmts", "true");

      this.connection =
          new com.github.paperorm.database.DataSourceDatabaseConnection(
              new com.zaxxer.hikari.HikariDataSource(config));
      return this;
    }

    public Builder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    public Builder useVirtualThreads() {
      this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
      return this;
    }

    public Builder scanner(EntityScanner scanner) {
      this.scanner = scanner;
      return this;
    }

    public Builder dialect(SqlDialect dialect) {
      this.dialect = dialect;
      return this;
    }

    public Builder typeMapper(TypeMapper typeMapper) {
      this.typeMapper = typeMapper;
      return this;
    }

    public <T> Builder registerConverter(com.github.paperorm.mapping.TypeConverter<T> converter) {
      this.typeMapper.registerConverter(converter);
      return this;
    }

    public Builder registerEntity(Class<?> entityClass) {
      this.registeredEntities.add(entityClass);
      return this;
    }

    public Builder autoCreateTables(boolean autoCreateTables) {
      this.autoCreateTables = autoCreateTables;
      return this;
    }

    public PaperOrm build() {
      var path = this.sqlitePath;
      var conn = this.connection;

      if (conn == null && path == null) {
        throw new IllegalStateException("Either connection or sqlite path must be specified.");
      }

      if (conn == null) {
        conn = new SqliteDatabaseConnection(path);
      }

      var factory =
          new OrmFactory(
              conn, this.scanner, this.dialect, this.typeMapper, this.executor, this.useCache);

      return new PaperOrm(
          conn, factory, this.registeredEntities, this.autoCreateTables, this.useCache);
    }
  }
}
