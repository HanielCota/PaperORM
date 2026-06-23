package com.github.paperorm.repository;

import com.github.paperorm.OrmSession;
import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.exception.OrmException;
import com.github.paperorm.mapping.EntityMapper;
import com.github.paperorm.mapping.EntityMetadata;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.repository.query.Query;
import com.github.paperorm.repository.query.Specification;
import com.github.paperorm.repository.query.SqlQuery;
import com.github.paperorm.schema.SchemaManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

public final class SqlRepository<T> implements Repository<T> {

  private final EntityMetadata metadata;
  private final DatabaseConnection databaseConnection;
  private final SqlDialect dialect;
  private final EntityMapper<T> entityMapper;
  private final TypeMapper typeMapper;
  private final Executor executor;
  private final IdentityMap<T> identityMap;
  private final CompletableFuture<Void> migrationsFuture;
  private volatile boolean migrationsAwaited;
  private final LifecycleDispatcher<T> lifecycle;
  private final SqlExecutor sqlExecutor;
  private final String entityClassName;

  public SqlRepository(
      Class<T> entityClass,
      DatabaseConnection databaseConnection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper) {
    this(
        entityClass,
        databaseConnection,
        scanner,
        dialect,
        typeMapper,
        ForkJoinPool.commonPool(),
        true,
        null);
  }

  public SqlRepository(
      Class<T> entityClass,
      DatabaseConnection databaseConnection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor) {
    this(entityClass, databaseConnection, scanner, dialect, typeMapper, executor, true, null);
  }

  public SqlRepository(
      Class<T> entityClass,
      DatabaseConnection databaseConnection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor,
      boolean useCache) {
    this(entityClass, databaseConnection, scanner, dialect, typeMapper, executor, useCache, null);
  }

  public SqlRepository(
      Class<T> entityClass,
      DatabaseConnection databaseConnection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor,
      boolean useCache,
      CompletableFuture<Void> migrationsFuture) {
    this.metadata = scanner.scan(entityClass);
    this.databaseConnection = databaseConnection;
    this.dialect = dialect;
    this.typeMapper = typeMapper;
    this.entityMapper = new EntityMapper<>(entityClass, typeMapper);
    this.executor = executor;
    this.identityMap = new IdentityMap<>(null, entityClass, useCache);
    this.migrationsFuture = migrationsFuture;
    this.lifecycle = new LifecycleDispatcher<>(entityClass);
    this.sqlExecutor = new SqlExecutor(databaseConnection);
    this.entityClassName = entityClass.getSimpleName();
  }

  public SqlRepository(Class<T> entityClass, SqlRepositoryConfig config) {
    this(
        entityClass,
        config.connection(),
        config.scanner(),
        config.dialect(),
        config.typeMapper(),
        config.executor(),
        config.useCache(),
        config.migrationsFuture());
  }

  public SqlRepository(
      Class<T> entityClass,
      OrmSession session,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor) {
    this(entityClass, session, scanner, dialect, typeMapper, executor, null);
  }

  public SqlRepository(
      Class<T> entityClass,
      OrmSession session,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor,
      CompletableFuture<Void> migrationsFuture) {
    this.metadata = scanner.scan(entityClass);
    this.databaseConnection = session.connection();
    this.dialect = dialect;
    this.typeMapper = typeMapper;
    this.entityMapper = new EntityMapper<>(entityClass, typeMapper);
    this.executor = executor;
    this.identityMap = new IdentityMap<>(session, entityClass, true);
    this.migrationsFuture = migrationsFuture;
    this.lifecycle = new LifecycleDispatcher<>(entityClass);
    this.sqlExecutor = new SqlExecutor(databaseConnection);
    this.entityClassName = entityClass.getSimpleName();
  }

  // -------------------- sync CRUD --------------------

  @Override
  public void ensureTable() {
    awaitMigrations();
    new SchemaManager(this.databaseConnection, this.dialect).ensureTable(this.metadata);
  }

  @Override
  public void save(T entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Entity cannot be null");
    }
    this.lifecycle.firePrePersist(entity);
    var idColumn = this.metadata.idColumn();
    var autoIncrement = idColumn.autoIncrement();

    awaitMigrations();
    this.sqlExecutor.execute(
        errorMsg("save"),
        connection -> {
          var sql = this.dialect.insert(this.metadata);
          try (var statement =
              connection.prepareStatement(
                  sql,
                  autoIncrement ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            bindInsertParameters(statement, entity);
            if (statement.executeUpdate() == 0) {
              throw new OrmException(errorMsg("save") + ", no rows affected");
            }
            if (autoIncrement) {
              assignGeneratedKey(statement, idColumn, entity);
            }
            registerInIdentityMap(entity, idColumn);
          }
          return null;
        });
  }

  @Override
  public void saveAll(Iterable<T> entities) {
    if (entities == null) {
      throw new IllegalArgumentException("Entities cannot be null");
    }
    var idColumn = this.metadata.idColumn();

    var entityList = new ArrayList<T>();
    for (var e : entities) {
      if (e != null) {
        entityList.add(e);
      }
    }
    if (entityList.isEmpty()) {
      return;
    }

    for (var entity : entityList) {
      this.lifecycle.firePrePersist(entity);
    }

    if (idColumn.autoIncrement()) {
      saveAllAutoIncrement(entityList, idColumn);
    } else {
      saveAllBatch(entityList);
    }
  }

  private void saveAllAutoIncrement(
      List<T> entities, com.github.paperorm.mapping.ColumnMetadata idColumn) {
    awaitMigrations();
    this.sqlExecutor.execute(
        errorMsg("saveAll"),
        connection -> {
          var sql = this.dialect.insert(this.metadata);
          try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (var entity : entities) {
              bindInsertParameters(statement, entity);
              if (statement.executeUpdate() == 0) {
                throw new OrmException(errorMsg("saveAll") + ", no rows affected");
              }
              try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                  var generatedId = keys.getLong(1);
                  this.entityMapper.setGeneratedId(idColumn, entity, generatedId);
                }
              }
            }
          }
          return null;
        });

    for (var entity : entities) {
      registerInIdentityMap(entity, idColumn);
    }
  }

  private void saveAllBatch(List<T> entities) {
    awaitMigrations();
    this.sqlExecutor.execute(
        errorMsg("saveAll"),
        connection -> {
          var sql = this.dialect.insert(this.metadata);
          try (var statement = connection.prepareStatement(sql, Statement.NO_GENERATED_KEYS)) {
            for (var entity : entities) {
              bindInsertParameters(statement, entity);
              statement.addBatch();
            }
            statement.executeBatch();
          }
          return null;
        });

    var idColumn = this.metadata.idColumn();
    for (var entity : entities) {
      registerInIdentityMap(entity, idColumn);
    }
  }

  @Override
  public void update(T entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Entity cannot be null");
    }
    this.lifecycle.firePreUpdate(entity);
    var idColumn = this.metadata.idColumn();
    var idValue = this.entityMapper.readField(idColumn, entity);
    if (idValue == null) {
      throw new IllegalArgumentException("Cannot update entity with null ID");
    }

    awaitMigrations();
    this.sqlExecutor.execute(
        errorMsg("update", idValue),
        connection -> {
          var sql = this.dialect.update(this.metadata);
          try (var statement = connection.prepareStatement(sql)) {
            bindUpdateParameters(statement, entity, idValue);
            if (statement.executeUpdate() == 0) {
              throw new OrmException(errorMsg("update", idValue) + ", entity not found");
            }
          }
          return null;
        });

    this.identityMap.register(idValue, entity);
  }

  @Override
  public void updateAll(Iterable<T> entities) {
    if (entities == null) {
      throw new IllegalArgumentException("Entities cannot be null");
    }
    var idColumn = this.metadata.idColumn();

    awaitMigrations();
    this.sqlExecutor.execute(
        errorMsg("updateAll"),
        connection -> {
          var sql = this.dialect.update(this.metadata);
          try (var statement = connection.prepareStatement(sql)) {
            for (var entity : entities) {
              if (entity == null) {
                continue;
              }
              this.lifecycle.firePreUpdate(entity);
              var idValue = this.entityMapper.readField(idColumn, entity);
              if (idValue == null) {
                throw new IllegalArgumentException("Cannot update entity with null ID");
              }
              bindUpdateParameters(statement, entity, idValue);
              statement.addBatch();
              this.identityMap.register(idValue, entity);
            }
            statement.executeBatch();
          }
          return null;
        });
  }

  @Override
  public void delete(T entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Entity cannot be null");
    }
    this.lifecycle.firePreDelete(entity);
    var idValue = this.entityMapper.readField(this.metadata.idColumn(), entity);
    deleteById(idValue);
  }

  @Override
  public void deleteById(Object id) {
    if (id == null) {
      throw new IllegalArgumentException("ID cannot be null");
    }
    var cached = this.identityMap.resolve(id);
    if (cached != null) {
      this.lifecycle.firePreDelete(cached);
    }

    awaitMigrations();
    this.sqlExecutor.execute(
        errorMsg("deleteById", id),
        connection -> {
          var sql = this.dialect.deleteById(this.metadata);
          try (var statement = connection.prepareStatement(sql)) {
            this.typeMapper.setParameter(statement, 1, id);
            statement.executeUpdate();
          }
          return null;
        });

    this.identityMap.evict(id);
  }

  // -------------------- sync queries --------------------

  @Override
  public Optional<T> findById(Object id) {
    if (id == null) {
      throw new IllegalArgumentException("ID cannot be null");
    }
    var cached = this.identityMap.resolve(id);
    if (cached != null) {
      return Optional.of(cached);
    }

    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("findById", id),
        connection -> {
          var sql = this.dialect.selectById(this.metadata);
          try (var statement = connection.prepareStatement(sql)) {
            this.typeMapper.setParameter(statement, 1, id);
            try (var resultSet = statement.executeQuery()) {
              if (!resultSet.next()) {
                return Optional.<T>empty();
              }
              var entity = this.entityMapper.mapRow(resultSet, this.metadata.columns());
              this.lifecycle.firePostLoad(entity);
              return Optional.of(cacheOrGet(entity));
            }
          }
        });
  }

  @Override
  public List<T> findAll() {
    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("findAll"),
        connection -> {
          var sql = this.dialect.selectAll(this.metadata);
          try (var statement = connection.prepareStatement(sql);
              var resultSet = statement.executeQuery()) {
            return mapResultSet(resultSet);
          }
        });
  }

  @Override
  public List<T> findBy(String column, Object value) {
    validateColumnExists(column);

    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("findBy"),
        connection -> {
          var sql = this.dialect.selectByColumn(this.metadata, column);
          try (var statement = connection.prepareStatement(sql)) {
            this.typeMapper.setParameter(statement, 1, value);
            try (var resultSet = statement.executeQuery()) {
              return mapResultSet(resultSet);
            }
          }
        });
  }

  @Override
  public boolean existsById(Object id) {
    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("existsById", id),
        connection -> {
          var sql = this.dialect.existsById(this.metadata);
          try (var statement = connection.prepareStatement(sql)) {
            this.typeMapper.setParameter(statement, 1, id);
            try (var resultSet = statement.executeQuery()) {
              return resultSet.next();
            }
          }
        });
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    var params = parameters != null ? parameters : new Object[0];

    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("findByQuery"),
        connection -> {
          var sql = this.dialect.selectAllWithCondition(this.metadata, whereClause);
          try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
              this.typeMapper.setParameter(statement, i + 1, params[i]);
            }
            try (var resultSet = statement.executeQuery()) {
              return mapResultSet(resultSet);
            }
          }
        });
  }

  @Override
  public long countByQuery(String whereClause, Object... parameters) {
    var params = parameters != null ? parameters : new Object[0];

    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("countByQuery"),
        connection -> {
          var baseSql =
              "SELECT COUNT(*) FROM " + this.dialect.quoteIdentifier(this.metadata.tableName());
          var sql = this.dialect.selectAllWithCondition(this.metadata, whereClause, baseSql);
          try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
              this.typeMapper.setParameter(statement, i + 1, params[i]);
            }
            try (var resultSet = statement.executeQuery()) {
              if (resultSet.next()) {
                return resultSet.getLong(1);
              }
              return 0L;
            }
          }
        });
  }

  @Override
  public List<T> find(Specification<T> spec) {
    return findByQuery(spec.toSql(this.dialect), spec.getParameters().toArray());
  }

  @Override
  public Query<T> select() {
    return new SqlQuery<>(this, this.dialect);
  }

  // -------------------- async wrappers --------------------

  @Override
  public CompletableFuture<Void> ensureTableAsync() {
    return asyncVoid(this::ensureTable);
  }

  @Override
  public CompletableFuture<Void> saveAsync(T entity) {
    return asyncVoid(() -> save(entity));
  }

  @Override
  public CompletableFuture<Void> saveAllAsync(Iterable<T> entities) {
    return asyncVoid(() -> saveAll(entities));
  }

  @Override
  public CompletableFuture<Void> updateAsync(T entity) {
    return asyncVoid(() -> update(entity));
  }

  @Override
  public CompletableFuture<Void> updateAllAsync(Iterable<T> entities) {
    return asyncVoid(() -> updateAll(entities));
  }

  @Override
  public CompletableFuture<Void> deleteAsync(T entity) {
    return asyncVoid(() -> delete(entity));
  }

  @Override
  public CompletableFuture<Void> deleteByIdAsync(Object id) {
    return asyncVoid(() -> deleteById(id));
  }

  @Override
  public CompletableFuture<Optional<T>> findByIdAsync(Object id) {
    return asyncSupply(() -> findById(id));
  }

  @Override
  public CompletableFuture<List<T>> findAllAsync() {
    return asyncSupply(this::findAll);
  }

  @Override
  public CompletableFuture<List<T>> findByAsync(String column, Object value) {
    return asyncSupply(() -> findBy(column, value));
  }

  @Override
  public CompletableFuture<Boolean> existsByIdAsync(Object id) {
    return asyncSupply(() -> existsById(id));
  }

  @Override
  public CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters) {
    return asyncSupply(() -> findByQuery(whereClause, parameters));
  }

  @Override
  public CompletableFuture<Long> countByQueryAsync(String whereClause, Object... parameters) {
    return asyncSupply(() -> countByQuery(whereClause, parameters));
  }

  @Override
  public CompletableFuture<List<T>> findAsync(Specification<T> spec) {
    return asyncSupply(() -> find(spec));
  }

  // -------------------- cache --------------------

  @Override
  public void clearCache() {
    this.identityMap.clear();
  }

  // -------------------- private helpers --------------------

  private CompletableFuture<Void> asyncVoid(Runnable action) {
    return CompletableFuture.runAsync(action, this.executor);
  }

  private <R> CompletableFuture<R> asyncSupply(Supplier<R> action) {
    return CompletableFuture.supplyAsync(action, this.executor);
  }

  private String errorMsg(String operation) {
    return "Failed to " + operation + " " + this.entityClassName;
  }

  private String errorMsg(String operation, Object id) {
    return "Failed to " + operation + " " + this.entityClassName + " with id " + id;
  }

  private void bindInsertParameters(PreparedStatement statement, T entity) throws SQLException {
    var parameterIndex = 1;
    for (var column : this.metadata.columns()) {
      if (column.autoIncrement()) {
        continue;
      }
      var value = this.entityMapper.readField(column, entity);
      this.typeMapper.setParameter(statement, parameterIndex, value);
      parameterIndex++;
    }
  }

  private void bindUpdateParameters(PreparedStatement statement, T entity, Object idValue)
      throws SQLException {
    var parameterIndex = 1;
    for (var column : this.metadata.columns()) {
      if (column.id()) {
        continue;
      }
      var value = this.entityMapper.readField(column, entity);
      this.typeMapper.setParameter(statement, parameterIndex, value);
      parameterIndex++;
    }
    this.typeMapper.setParameter(statement, parameterIndex, idValue);
  }

  private void assignGeneratedKey(
      Statement statement, com.github.paperorm.mapping.ColumnMetadata idColumn, T entity)
      throws SQLException {
    try (var keys = statement.getGeneratedKeys()) {
      if (keys.next()) {
        var generatedId = keys.getLong(1);
        this.entityMapper.setGeneratedId(idColumn, entity, generatedId);
      }
    }
  }

  private void registerInIdentityMap(
      T entity, com.github.paperorm.mapping.ColumnMetadata idColumn) {
    var idValue = this.entityMapper.readField(idColumn, entity);
    if (idValue != null) {
      this.identityMap.register(idValue, entity);
    }
  }

  private List<T> mapResultSet(ResultSet resultSet) throws SQLException {
    var entities = new ArrayList<T>();
    var columns = this.metadata.columns();
    while (resultSet.next()) {
      var entity = this.entityMapper.mapRow(resultSet, columns);
      this.lifecycle.firePostLoad(entity);
      entities.add(cacheOrGet(entity));
    }
    return entities;
  }

  private void validateColumnExists(String column) {
    var valid =
        this.metadata.columns().stream()
            .map(com.github.paperorm.mapping.ColumnMetadata::columnName)
            .anyMatch(column::equals);
    if (!valid) {
      throw new IllegalArgumentException(
          "Column " + column + " not found in entity " + this.entityClassName);
    }
  }

  private void awaitMigrations() {
    if (!this.migrationsAwaited && this.migrationsFuture != null) {
      this.migrationsFuture.join();
      this.migrationsAwaited = true;
    }
  }

  private T cacheOrGet(T entity) {
    var idColumn = this.metadata.idColumn();
    var idValue = this.entityMapper.readField(idColumn, entity);
    if (idValue == null) {
      return entity;
    }
    return this.identityMap.cacheOrGet(idValue, entity);
  }
}
