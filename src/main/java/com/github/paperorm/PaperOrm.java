package com.github.paperorm;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.IdResolver;
import com.github.paperorm.mapping.JsonTypeConverter;
import com.github.paperorm.mapping.ReflectionEntityScanner;
import com.github.paperorm.mapping.TypeConverter;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.migration.Migration;
import com.github.paperorm.migration.MigrationRunner;
import com.github.paperorm.repository.Repository;
import com.google.gson.Gson;
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

public final class PaperOrm implements AutoCloseable {

  private final OrmContext context;
  private final OrmFactory factory;
  private final OrmSession defaultSession;

  private PaperOrm(
      OrmContext context,
      OrmFactory factory,
      List<Class<?>> registeredEntities,
      boolean autoCreateTables) {
    this.context = context;
    this.factory = factory;
    this.defaultSession = new OrmSession(context, factory);

    if (autoCreateTables && !registeredEntities.isEmpty()) {
      var futures =
          registeredEntities.stream()
              .map(
                  entityClass ->
                      CompletableFuture.runAsync(
                          () -> this.defaultSession.getRepository(entityClass).ensureTable(),
                          context.executor()))
              .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(futures).join();
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static PaperOrm sqlite(Path path, Class<?>... entities) {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(entities, "entities");
    return builder()
        .connectionConfig(new ConnectionConfig.Sqlite(path))
        .useVirtualThreads()
        .autoCreateTables(true)
        .registerEntities(entities)
        .build();
  }

  public <T> Repository<T> getRepository(Class<T> entityClass) {
    Objects.requireNonNull(entityClass, "entityClass");
    return this.defaultSession.getRepository(entityClass);
  }

  public DatabaseConnection connection() {
    return this.context.connection();
  }

  public OrmSession openSession() {
    return new OrmSession(this.context, this.factory);
  }

  public void awaitMigrations() {
    if (this.context.migrationsFuture() != null) {
      this.context.migrationsFuture().join();
    }
  }

  public boolean hasPendingMigrations() {
    return this.context.migrationsFuture() != null && !this.context.migrationsFuture().isDone();
  }

  @Override
  public void close() {
    this.defaultSession.close();
    this.context.connection().close();
    if (this.context.ownsExecutor()
        && this.context.executor() instanceof ExecutorService service
        && !service.isShutdown()) {
      service.shutdown();
    }
  }

  public static final class Builder {

    private static final Gson SHARED_GSON = new Gson();

    private ConnectionConfig connectionConfig;
    private Executor executor = ForkJoinPool.commonPool();
    private boolean ownsExecutor = false;
    private TypeMapper typeMapper = new TypeMapper();
    private EntityScanner scanner = new ReflectionEntityScanner(typeMapper);
    private SqlDialect dialect;
    private final List<Class<?>> registeredEntities = new ArrayList<>();
    private boolean autoCreateTables = false;
    private boolean useCache = true;
    private Logger logger;
    private final List<Migration> migrations = new ArrayList<>();

    public Builder useCache(boolean useCache) {
      this.useCache = useCache;
      return this;
    }

    public Builder connectionConfig(ConnectionConfig connectionConfig) {
      this.connectionConfig = Objects.requireNonNull(connectionConfig, "connectionConfig");
      return this;
    }

    public Builder logger(Logger logger) {
      this.logger = logger;
      return this;
    }

    public Builder migrations(List<Migration> migrations) {
      if (migrations != null) {
        this.migrations.addAll(migrations);
      }
      return this;
    }

    public Builder executor(Executor executor) {
      this.executor = executor;
      return this;
    }

    public Builder useVirtualThreads() {
      this.executor = Executors.newVirtualThreadPerTaskExecutor();
      this.ownsExecutor = true;
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
      if (this.scanner instanceof ReflectionEntityScanner) {
        this.scanner = new ReflectionEntityScanner(typeMapper);
      }
      return this;
    }

    public <T> Builder registerConverter(TypeConverter<T> converter) {
      this.typeMapper.registerConverter(converter);
      return this;
    }

    public <T> Builder registerJsonConverter(Class<T> type) {
      this.typeMapper.registerConverter(new JsonTypeConverter<>(type, SHARED_GSON));
      return this;
    }

    public Builder registerEntity(Class<?> entityClass) {
      this.registeredEntities.add(entityClass);
      return this;
    }

    public Builder registerEntities(Class<?>... entityClasses) {
      this.registeredEntities.addAll(List.of(entityClasses));
      return this;
    }

    public Builder autoCreateTables(boolean autoCreateTables) {
      this.autoCreateTables = autoCreateTables;
      return this;
    }

    public PaperOrm build() {
      if (this.connectionConfig == null) {
        throw new IllegalStateException("connectionConfig must be specified.");
      }

      var finalDialect =
          this.dialect != null ? this.dialect : this.connectionConfig.defaultDialect();
      var finalConn = this.connectionConfig.createConnection(this.logger);

      try {
        var migrationsFuture = startMigrationsIfNeeded(finalConn, finalDialect);
        var context =
            new OrmContext(
                finalConn,
                this.scanner,
                finalDialect,
                this.typeMapper,
                new IdResolver(),
                this.executor,
                this.useCache,
                this.ownsExecutor,
                migrationsFuture);
        var factory = new OrmFactory(context);

        return new PaperOrm(context, factory, this.registeredEntities, this.autoCreateTables);
      } catch (RuntimeException exception) {
        closeConnectionOnFailure(finalConn);
        throw exception;
      }
    }

    private CompletableFuture<Void> startMigrationsIfNeeded(
        DatabaseConnection connection, SqlDialect dialect) {
      if (this.migrations.isEmpty()) {
        return null;
      }
      var runner = new MigrationRunner(dialect);
      var migrationList = List.copyOf(this.migrations);
      return CompletableFuture.runAsync(() -> runner.run(connection, migrationList), this.executor);
    }

    private void closeConnectionOnFailure(DatabaseConnection connection) {
      try {
        connection.close();
      } catch (Exception closeException) {
        // suppressed - original exception is more important
      }
    }
  }
}
