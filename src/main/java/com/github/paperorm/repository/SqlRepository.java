package com.github.paperorm.repository;

import com.github.paperorm.OrmSession;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
  private final OrmSession session;
  private final java.util.Map<Object, java.lang.ref.WeakReference<T>> cache;

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
        true);
  }

  public SqlRepository(
      Class<T> entityClass,
      DatabaseConnection databaseConnection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor) {
    this(entityClass, databaseConnection, scanner, dialect, typeMapper, executor, true);
  }

  public SqlRepository(
      Class<T> entityClass,
      DatabaseConnection databaseConnection,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor,
      boolean useCache) {
    this(
        entityClass,
        new OrmSession(databaseConnection, null, useCache),
        scanner,
        dialect,
        typeMapper,
        executor);
  }

  public SqlRepository(
      Class<T> entityClass,
      OrmSession session,
      EntityScanner scanner,
      SqlDialect dialect,
      TypeMapper typeMapper,
      Executor executor) {
    this.metadata = scanner.scan(entityClass);
    this.session = session;
    this.databaseConnection = session.connection();
    this.dialect = dialect;
    this.typeMapper = typeMapper;
    this.entityMapper = new EntityMapper<>(entityClass, typeMapper);
    this.executor = executor;
    this.useCache = session.isUseCache();
    this.cache = session.getCache(entityClass);
  }

  @Override
  public void ensureTable() {
    var schemaManager = new SchemaManager(this.databaseConnection, this.dialect);
    schemaManager.ensureTable(this.metadata);
  }

  @Override
  public void save(T entity) {
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
        this.cache.put(idValue, new java.lang.ref.WeakReference<>(entity));
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to save entity", exception);
    }
  }

  @Override
  public void update(T entity) {
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
        this.cache.put(idValue, new java.lang.ref.WeakReference<>(entity));
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to update entity", exception);
    }
  }

  @Override
  public void deleteById(Object id) {
    if (id == null) {
      throw new IllegalArgumentException("ID cannot be null");
    }
    var sql = this.dialect.deleteById(this.metadata);

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql)) {
      this.typeMapper.setParameter(statement, 1, id);

      if (statement.executeUpdate() == 0) {
        throw new OrmException("Failed to delete entity, entity not found");
      }

      if (this.useCache) {
        this.cache.remove(id);
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to delete entity", exception);
    }
  }

  @Override
  public Optional<T> findById(Object id) {
    if (id == null) {
      throw new IllegalArgumentException("ID cannot be null");
    }
    if (this.useCache) {
      var ref = this.cache.get(id);
      if (ref != null) {
        var cached = ref.get();
        if (cached != null) {
          return Optional.of(cached);
        }
        this.cache.remove(id);
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
        return Optional.of(cacheOrGet(entity));
      }
    } catch (SQLException exception) {
      throw new OrmException("Failed to find entity by id", exception);
    }
  }

  @Override
  public List<T> findAll() {
    var sql = this.dialect.selectAll(this.metadata);

    try (var connection = this.databaseConnection.openConnection();
        var statement = connection.prepareStatement(sql);
        var resultSet = statement.executeQuery()) {
      var entities = new ArrayList<T>();
      var columns = this.metadata.columns();

      while (resultSet.next()) {
        var entity = this.entityMapper.mapRow(resultSet, columns);
        entities.add(cacheOrGet(entity));
      }

      return entities;
    } catch (SQLException exception) {
      throw new OrmException("Failed to find all entities", exception);
    }
  }

  @Override
  public List<T> findBy(String column, Object value) {
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

  private void execute(String sql) {
    try {
      this.databaseConnection.execute(sql);
    } catch (SQLException exception) {
      throw new OrmException("Failed to execute SQL", exception);
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

  private void pruneCache() {
    if (this.useCache) {
      this.cache.values().removeIf(ref -> ref.get() == null);
    }
  }

  private T cacheOrGet(T entity) {
    if (!this.useCache) {
      return entity;
    }
    pruneCache();
    var idColumn = this.metadata.idColumn();
    var idValue = this.entityMapper.readField(idColumn, entity);
    if (idValue == null) {
      return entity;
    }
    var ref = this.cache.get(idValue);
    if (ref != null) {
      var cached = ref.get();
      if (cached != null) {
        return cached;
      }
    }
    this.cache.put(idValue, new java.lang.ref.WeakReference<>(entity));
    return entity;
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    var baseSql = this.dialect.selectAll(this.metadata);
    var sql = baseSql + " WHERE " + whereClause;

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
  public Query<T> select() {
    return new SqlQuery<>(this, this.dialect);
  }
}
