package com.github.paperorm;

import com.github.paperorm.database.DataSourceDatabaseConnection;
import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.dialect.MySqlDialect;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.dialect.SqliteDialect;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.JsonTypeConverter;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeConverter;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.migration.Migration;
import com.github.paperorm.migration.MigrationRunner;
import com.github.paperorm.repository.Repository;
import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;

/**
 * Main entry point for PaperORM, a lightweight asynchronous ORM for PaperSpigot Minecraft plugins.
 *
 * <p>Supports SQLite (local embedded) and MySQL (via HikariCP connection pool). Provides fluent
 * configuration via {@link Builder}, virtual thread support for minimal TPS impact, identity-map
 * caching, automatic schema management, and versioned migrations.
 *
 * <h3>Quick start</h3>
 *
 * <pre>{@code
 * PaperOrm orm = PaperOrm.sqlite(pluginDataFolder.resolve("database.db"), PlayerEntity.class);
 * Repository<PlayerEntity> repo = orm.getRepository(PlayerEntity.class);
 *
 * var player = new PlayerEntity();
 * player.setName("Steve");
 * repo.save(player);
 * }</pre>
 *
 * <h3>MySQL</h3>
 *
 * <pre>{@code
 * PaperOrm orm = PaperOrm.builder()
 *     .mysql("localhost", 3306, "mydb", "user", "pass")
 *     .registerEntity(PlayerEntity.class)
 *     .autoCreateTables(true)
 *     .build();
 * }</pre>
 *
 * <p>Implements {@link AutoCloseable} — call {@link #close()} on plugin disable to release
 * connections.
 */
public final class PaperOrm implements AutoCloseable {

  private final DatabaseConnection connection;
  private final OrmFactory factory;
  private final OrmSession defaultSession;
  private final CompletableFuture<Void> migrationsFuture;
  private final Executor executor;

  private PaperOrm(
      DatabaseConnection connection,
      OrmFactory factory,
      List<Class<?>> registeredEntities,
      boolean autoCreateTables,
      boolean useCache,
      CompletableFuture<Void> migrationsFuture,
      Executor executor) {
    this.connection = connection;
    this.factory = factory;
    this.migrationsFuture = migrationsFuture;
    this.defaultSession = new OrmSession(connection, factory, useCache);
    this.executor = executor;

    for (var entityClass : registeredEntities) {
      var repo = this.defaultSession.getRepository(entityClass);
      if (autoCreateTables) {
        repo.ensureTable();
      }
    }
  }

  /** Creates a new {@link Builder} for fluent configuration. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Convenience method for creating a SQLite-based PaperOrm with virtual threads and auto-create
   * enabled.
   *
   * @param path path to the SQLite database file
   * @param entities entity classes to register
   * @return a fully configured PaperOrm instance
   */
  public static PaperOrm sqlite(Path path, Class<?>... entities) {
    var builder = builder().sqlite(path).useVirtualThreads().autoCreateTables(true);
    for (var entity : entities) {
      builder.registerEntity(entity);
    }
    return builder.build();
  }

  /**
   * Returns the repository for the given entity type. The entity class must be registered via
   * {@link Builder#registerEntity(Class)}.
   *
   * @param entityClass the entity class
   * @return a repository for CRUD operations
   */
  public <T> Repository<T> getRepository(Class<T> entityClass) {
    Objects.requireNonNull(entityClass, "entityClass");
    return this.defaultSession.getRepository(entityClass);
  }

  /** Returns the underlying database connection. */
  public DatabaseConnection connection() {
    return this.connection;
  }

  /**
   * Opens a new session with an isolated identity-map cache. Sessions are useful for request-scoped
   * caching and transactional boundaries.
   *
   * @return a new {@link OrmSession}
   */
  public OrmSession openSession() {
    return new OrmSession(this.connection, this.factory, this.defaultSession.isUseCache());
  }

  /** Blocks until all pending migrations have completed. */
  public void awaitMigrations() {
    if (this.migrationsFuture != null) {
      this.migrationsFuture.join();
    }
  }

  /** Returns {@code true} if migrations are still running. */
  public boolean hasPendingMigrations() {
    return this.migrationsFuture != null && !this.migrationsFuture.isDone();
  }

  @Override
  public void close() {
    this.defaultSession.close();
    this.connection.close();
    if (this.executor instanceof ExecutorService service && !service.isShutdown()) {
      service.shutdown();
    }
  }

  /**
   * Fluent builder for configuring a {@link PaperOrm} instance. Supports SQLite and MySQL, custom
   * type converters, entity registration, migrations, and executor configuration.
   */
  public static final class Builder {

    private Path sqlitePath;
    private DatabaseConnection connection;
    private Executor executor = ForkJoinPool.commonPool();
    private EntityScanner scanner = new ReflectionEntityScanner();
    private SqlDialect dialect = new SqliteDialect();
    private TypeMapper typeMapper = new TypeMapper();
    private final List<Class<?>> registeredEntities = new ArrayList<>();
    private boolean autoCreateTables = false;
    private boolean useCache = true;
    private Logger logger;
    private final List<Migration> migrations = new ArrayList<>();
    private final Gson gson = new Gson();

    /** Enables or disables the identity-map cache (enabled by default). */
    public Builder useCache(boolean useCache) {
      this.useCache = useCache;
      return this;
    }

    /**
     * Configures SQLite as the database backend.
     *
     * @param path path to the SQLite database file
     */
    public Builder sqlite(Path path) {
      this.sqlitePath = path;
      this.dialect = new SqliteDialect();
      return this;
    }

    /** Sets a custom {@link DatabaseConnection}. */
    public Builder connection(DatabaseConnection connection) {
      this.connection = connection;
      return this;
    }

    /** Sets a custom logger for the connection pool. */
    public Builder logger(Logger logger) {
      this.logger = logger;
      return this;
    }

    /**
     * Registers migrations to run before any entity operations. Migrations are executed
     * asynchronously on the configured executor.
     */
    public Builder migrations(List<Migration> migrations) {
      if (migrations != null) {
        this.migrations.addAll(migrations);
      }
      return this;
    }

    /**
     * Configures MySQL as the database backend using HikariCP.
     *
     * @param host the MySQL host
     * @param port the MySQL port
     * @param database the database name
     * @param username the username
     * @param password the password
     */
    public Builder mysql(String host, int port, String database, String username, String password) {
      var config = new HikariConfig();
      config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
      config.setUsername(username);
      config.setPassword(password);
      config.setMaximumPoolSize(10);
      config.addDataSourceProperty("cachePrepStmts", "true");
      config.addDataSourceProperty("prepStmtCacheSize", "250");
      config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
      config.addDataSourceProperty("useServerPrepStmts", "true");

      this.connection = new DataSourceDatabaseConnection(new HikariDataSource(config), this.logger);
      this.dialect = new MySqlDialect();
      return this;
    }

    /** Sets the executor for async operations (defaults to {@link ForkJoinPool#commonPool()}). */
    public Builder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    /** Enables Java virtual threads for async database operations. */
    public Builder useVirtualThreads() {
      this.executor = Executors.newVirtualThreadPerTaskExecutor();
      return this;
    }

    /** Sets a custom {@link EntityScanner} implementation. */
    public Builder scanner(EntityScanner scanner) {
      this.scanner = scanner;
      return this;
    }

    /** Sets a custom {@link SqlDialect} implementation. */
    public Builder dialect(SqlDialect dialect) {
      this.dialect = dialect;
      return this;
    }

    /** Sets a custom {@link TypeMapper} with pre-configured converters. */
    public Builder typeMapper(TypeMapper typeMapper) {
      this.typeMapper = typeMapper;
      return this;
    }

    /** Registers a custom {@link TypeConverter}. */
    public <T> Builder registerConverter(TypeConverter<T> converter) {
      this.typeMapper.registerConverter(converter);
      return this;
    }

    /**
     * Registers a JSON converter for the given type using Gson serialization. The type is stored as
     * TEXT in the database.
     */
    public <T> Builder registerJsonConverter(Class<T> type) {
      this.typeMapper.registerConverter(new JsonTypeConverter<>(type, gson));
      return this;
    }

    /** Registers an entity class for management by PaperORM. */
    public Builder registerEntity(Class<?> entityClass) {
      this.registeredEntities.add(entityClass);
      return this;
    }

    /** Enables automatic table creation on build (disabled by default). */
    public Builder autoCreateTables(boolean autoCreateTables) {
      this.autoCreateTables = autoCreateTables;
      return this;
    }

    /**
     * Builds the PaperOrm instance.
     *
     * @return a fully configured {@link PaperOrm}
     * @throws IllegalStateException if neither connection nor sqlite path is specified
     */
    public PaperOrm build() {
      var finalConn = resolveConnection();
      try {
        var migrationsFuture = startMigrationsIfNeeded(finalConn);
        var factory = createOrmFactory(finalConn, migrationsFuture);

        return new PaperOrm(
            finalConn,
            factory,
            this.registeredEntities,
            this.autoCreateTables,
            this.useCache,
            migrationsFuture,
            this.executor);
      } catch (RuntimeException exception) {
        closeConnectionOnFailure(finalConn);
        throw exception;
      }
    }

    private DatabaseConnection resolveConnection() {
      if (this.connection != null) {
        return this.connection;
      }
      if (this.sqlitePath != null) {
        return new SqliteDatabaseConnection(this.sqlitePath, this.logger);
      }
      throw new IllegalStateException("Either connection or sqlite path must be specified.");
    }

    private CompletableFuture<Void> startMigrationsIfNeeded(DatabaseConnection connection) {
      if (this.migrations.isEmpty()) {
        return null;
      }
      var runner = new MigrationRunner(this.dialect);
      var migrationList = List.copyOf(this.migrations);
      return CompletableFuture.runAsync(() -> runner.run(connection, migrationList), this.executor);
    }

    private OrmFactory createOrmFactory(
        DatabaseConnection connection, CompletableFuture<Void> migrationsFuture) {
      return new OrmFactory(
          connection,
          this.scanner,
          this.dialect,
          this.typeMapper,
          this.executor,
          this.useCache,
          migrationsFuture);
    }

    private void closeConnectionOnFailure(DatabaseConnection connection) {
      if (this.connection != null) {
        return;
      }
      try {
        connection.close();
      } catch (Exception closeException) {
        // suppressed - original exception is more important
      }
    }
  }
}
