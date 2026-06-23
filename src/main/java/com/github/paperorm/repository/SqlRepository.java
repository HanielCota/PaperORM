package com.github.paperorm.repository;

import com.github.paperorm.OrmSession;
import com.github.paperorm.annotation.PostLoad;
import com.github.paperorm.annotation.PreDelete;
import com.github.paperorm.annotation.PrePersist;
import com.github.paperorm.annotation.PreUpdate;
import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.exception.OrmException;
import com.github.paperorm.mapping.EntityMapper;
import com.github.paperorm.mapping.EntityMetadata;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.schema.SchemaManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class SqlRepository<T> implements Repository<T> {

  private final EntityMetadata metadata;
  private final DatabaseConnection databaseConnection;
  private final SqlDialect dialect;
  private final EntityMapper<T> entityMapper;
  private final TypeMapper typeMapper;
  private final Executor executor;
  private final boolean useCache;
  private final Map<Object, T> cache;
  private final OrmSession session;
  private final CompletableFuture<Void> migrationsFuture;
  private volatile boolean migrationsAwaited = false;

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
    this.useCache = useCache;
    this.cache = new ConcurrentHashMap<>();
    this.session = null;
    this.migrationsFuture = migrationsFuture;
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
    this.useCache = session.isUseCache();
    this.cache = session.getCache(entityClass);
    this.session = session;
    this.migrationsFuture = migrationsFuture;
  }

  private void awaitMigrations() {
    if (!this.migrationsAwaited && this.migrationsFuture != null) {
      this.migrationsFuture.join();
      this.migrationsAwaited = true;
    }
  }

  @Override
  public void ensureTable() {
    awaitMigrations();
    new SchemaManager(this.databaseConnection, this.dialect).ensureTable(this.metadata);
  }

  @Override
  public void save(T entity) {
    awaitMigrations();
    if (entity == null) {
      throw new IllegalArgumentException("Entity cannot be null");
    }
    fireEvent(entity, PrePersist.class);
    var sql = this.dialect.insert(this.metadata);
    var idColumn = this.metadata.idColumn();
    var autoIncrement = idColumn.autoIncrement();

    try (var connection = this.databaseConnection.openConnection();
        var statement =
            connection.prepareStatement(
                sql,
                autoIncrement ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
      var parameterIndex = 1;

      for (var column : this.metadata.columns()) {
        if (column.autoIncrement()) {
          continue;
        }

        var value = this.entityMapper.readField(column, entity);
        this.typeMapper.setParameter(statement, parameterIndex, value);
        parameterIndex++;
      }

      if (statement.executeUpdate() == 0) {
        throw new OrmException("Failed to save entity, no rows affected");
      }

      if (autoIncrement) {
        try (var keys = statement.getGeneratedKeys()) {
          if (keys.next()) {
            var generatedId = keys.getLong(1);
            this.entityMapper.setGeneratedId(idColumn, entity, generatedId);
          }
        }
      }

      var idValue = this.entityMapper.readField(idColumn, entity);
      if (this.useCache && idValue != null) {
        registerIdentity(idValue, entity);
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to save entity", exception);
    }
  }

  @Override
  public void update(T entity) {
    awaitMigrations();
    if (entity == null) {
      throw new IllegalArgumentException("Entity cannot be null");
    }
    fireEvent(entity, PreUpdate.class);
    var idColumn = this.metadata.idColumn();
    var idValue = this.entityMapper.readField(idColumn, entity);
    if (idValue == null) {
      throw new IllegalArgumentException("Cannot update entity with null ID");
    }

    var sql = this.dialect.update(this.metadata);

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql)) {
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

      if (statement.executeUpdate() == 0) {
        throw new OrmException("Failed to update entity, entity not found");
      }

      if (this.useCache) {
        registerIdentity(idValue, entity);
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to update entity", exception);
    }
  }

  @Override
  public void delete(T entity) {
    awaitMigrations();
    if (entity == null) {
      throw new IllegalArgumentException("Entity cannot be null");
    }
    fireEvent(entity, PreDelete.class);
    var idValue = this.entityMapper.readField(this.metadata.idColumn(), entity);
    deleteById(idValue);
  }

  @Override
  public void deleteById(Object id) {
    awaitMigrations();
    if (id == null) {
      throw new IllegalArgumentException("ID cannot be null");
    }

    if (this.useCache) {
      var cached = resolveIdentity(id);
      if (cached != null) {
        fireEvent(cached, PreDelete.class);
      }
    }

    var sql = this.dialect.deleteById(this.metadata);

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql)) {
      this.typeMapper.setParameter(statement, 1, id);
      statement.executeUpdate();

      if (this.useCache) {
        evictIdentity(id);
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to delete entity", exception);
    }
  }

  @Override
  public Optional<T> findById(Object id) {
    awaitMigrations();
    if (id == null) {
      throw new IllegalArgumentException("ID cannot be null");
    }
    if (this.useCache) {
      var cached = resolveIdentity(id);
      if (cached != null) {
        return Optional.of(cached);
      }
    }

    var sql = this.dialect.selectById(this.metadata);

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql)) {
      this.typeMapper.setParameter(statement, 1, id);

      try (var resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }

        var columns = this.metadata.columns();
        var entity = this.entityMapper.mapRow(resultSet, columns);
        fireEvent(entity, PostLoad.class);
        return Optional.of(cacheOrGet(entity));
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to find entity by id", exception);
    }
  }

  @Override
  public List<T> findAll() {
    awaitMigrations();
    var sql = this.dialect.selectAll(this.metadata);

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql);
        var resultSet = statement.executeQuery()) {
      var entities = new ArrayList<T>();
      var columns = this.metadata.columns();

      while (resultSet.next()) {
        var entity = this.entityMapper.mapRow(resultSet, columns);
        fireEvent(entity, PostLoad.class);
        entities.add(cacheOrGet(entity));
      }

      return entities;
    } catch (SQLException exception) {
      throw new OrmException("Failed to find all entities", exception);
    }
  }

  @Override
  public List<T> findBy(String column, Object value) {
    awaitMigrations();
    validateColumn(column);

    var sql = this.dialect.selectByColumn(this.metadata, column);

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql)) {
      this.typeMapper.setParameter(statement, 1, value);

      try (var resultSet = statement.executeQuery()) {
        var entities = new ArrayList<T>();
        var columns = this.metadata.columns();

        while (resultSet.next()) {
          var entity = this.entityMapper.mapRow(resultSet, columns);
          fireEvent(entity, PostLoad.class);
          entities.add(cacheOrGet(entity));
        }

        return entities;
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to find entities by column", exception);
    }
  }

  @Override
  public boolean existsById(Object id) {
    awaitMigrations();
    var sql = this.dialect.existsById(this.metadata);

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql)) {
      this.typeMapper.setParameter(statement, 1, id);

      try (var resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to check entity existence", exception);
    }
  }

  private void validateColumn(String column) {
    var columns = this.metadata.columns();
    var valid = columns.stream().anyMatch(c -> c.columnName().equals(column));

    if (!valid) {
      var entityClass = this.metadata.entityClass();
      var className = entityClass.getName();
      throw new IllegalArgumentException("Column " + column + " not found in entity " + className);
    }
  }

  @Override
  public CompletableFuture<Void> ensureTableAsync() {
    return CompletableFuture.runAsync(this::ensureTable, this.executor);
  }

  @Override
  public CompletableFuture<Void> saveAsync(T entity) {
    return CompletableFuture.runAsync(() -> save(entity), this.executor);
  }

  @Override
  public CompletableFuture<Void> updateAsync(T entity) {
    return CompletableFuture.runAsync(() -> update(entity), this.executor);
  }

  @Override
  public CompletableFuture<Void> deleteAsync(T entity) {
    return CompletableFuture.runAsync(() -> delete(entity), this.executor);
  }

  @Override
  public CompletableFuture<Void> deleteByIdAsync(Object id) {
    return CompletableFuture.runAsync(() -> deleteById(id), this.executor);
  }

  @Override
  public CompletableFuture<Optional<T>> findByIdAsync(Object id) {
    return CompletableFuture.supplyAsync(() -> findById(id), this.executor);
  }

  @Override
  public CompletableFuture<List<T>> findAllAsync() {
    return CompletableFuture.supplyAsync(this::findAll, this.executor);
  }

  @Override
  public CompletableFuture<List<T>> findByAsync(String column, Object value) {
    return CompletableFuture.supplyAsync(() -> findBy(column, value), this.executor);
  }

  @Override
  public CompletableFuture<Boolean> existsByIdAsync(Object id) {
    return CompletableFuture.supplyAsync(() -> existsById(id), this.executor);
  }

  @Override
  public void clearCache() {
    this.cache.clear();
  }

  private T cacheOrGet(T entity) {
    if (!this.useCache) {
      return entity;
    }
    var idColumn = this.metadata.idColumn();
    var idValue = this.entityMapper.readField(idColumn, entity);
    if (idValue == null) {
      return entity;
    }
    var existing = resolveIdentity(idValue);
    if (existing != null) {
      return existing;
    }
    registerIdentity(idValue, entity);
    return entity;
  }

  @SuppressWarnings("unchecked")
  private T resolveIdentity(Object id) {
    if (this.session != null) {
      return this.session.getIdentity((Class<T>) this.metadata.entityClass(), id);
    }
    return this.cache.get(id);
  }

  @SuppressWarnings("unchecked")
  private void registerIdentity(Object id, T entity) {
    if (this.session != null) {
      this.session.registerIdentity((Class<T>) this.metadata.entityClass(), id, entity);
      return;
    }
    this.cache.put(id, entity);
  }

  private void evictIdentity(Object id) {
    if (this.session != null) {
      this.session.evictIdentity(this.metadata.entityClass(), id);
      return;
    }
    this.cache.remove(id);
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    awaitMigrations();
    var baseSql = this.dialect.selectAll(this.metadata);
    var trimmed = whereClause.trim();
    var hasCondition =
        !trimmed.isEmpty()
            && !trimmed.regionMatches(true, 0, "ORDER BY", 0, 8)
            && !trimmed.regionMatches(true, 0, "LIMIT", 0, 5)
            && !trimmed.regionMatches(true, 0, "OFFSET", 0, 6);
    var sql = hasCondition ? baseSql + " WHERE " + whereClause : baseSql + " " + whereClause;

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql)) {

      for (int i = 0; i < parameters.length; i++) {
        this.typeMapper.setParameter(statement, i + 1, parameters[i]);
      }

      try (var resultSet = statement.executeQuery()) {
        var entities = new ArrayList<T>();
        var columns = this.metadata.columns();

        while (resultSet.next()) {
          var entity = this.entityMapper.mapRow(resultSet, columns);
          fireEvent(entity, PostLoad.class);
          entities.add(cacheOrGet(entity));
        }

        return entities;
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to find entities by query", exception);
    }
  }

  @Override
  public CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters) {
    return CompletableFuture.supplyAsync(() -> findByQuery(whereClause, parameters), this.executor);
  }

  @Override
  public List<T> find(Specification<T> spec) {
    return findByQuery(spec.toSql(this.dialect), spec.getParameters().toArray());
  }

  @Override
  public CompletableFuture<List<T>> findAsync(Specification<T> spec) {
    return CompletableFuture.supplyAsync(() -> find(spec), this.executor);
  }

  @Override
  public Query<T> select() {
    return new SqlQuery<>(this, this.dialect);
  }

  private void fireEvent(
      T entity, Class<? extends java.lang.annotation.Annotation> annotationClass) {
    if (entity == null) {
      return;
    }
    var clazz = entity.getClass();
    while (clazz != null && clazz != Object.class) {
      for (var method : clazz.getDeclaredMethods()) {
        if (method.isAnnotationPresent(annotationClass)) {
          try {
            method.setAccessible(true);
            method.invoke(entity);
          } catch (ReflectiveOperationException e) {
            throw new OrmException(
                "Failed to invoke @"
                    + annotationClass.getSimpleName()
                    + " method on "
                    + entity.getClass().getSimpleName(),
                e);
          }
        }
      }
      clazz = clazz.getSuperclass();
    }
  }
}
