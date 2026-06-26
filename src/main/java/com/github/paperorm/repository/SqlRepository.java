package com.github.paperorm.repository;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.exception.OrmException;
import com.github.paperorm.mapping.EntityMapper;
import com.github.paperorm.mapping.EntityMetadata;
import com.github.paperorm.mapping.IdResolver;
import com.github.paperorm.mapping.TypeMapper;
import com.github.paperorm.repository.query.Query;
import com.github.paperorm.repository.query.Spec;
import com.github.paperorm.repository.query.Specification;
import com.github.paperorm.schema.SchemaManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.Getter;

public final class SqlRepository<T> implements Repository<T> {

  private static final String ENTITY_NULL_MSG = "Entity cannot be null";
  private static final String ID_NULL_MSG = "ID cannot be null";
  private static final String SAVE_ALL = "saveAll";

  private final EntityMetadata metadata;
  private final DatabaseConnection databaseConnection;
  private final SqlDialect dialect;
  private final EntityMapper<T> entityMapper;
  private final TypeMapper typeMapper;
  @Getter private final Executor executor;
  private final IdResolver idResolver;
  private final IdentityMap<T> identityMap;
  private final CompletableFuture<Void> migrationsFuture;
  private volatile boolean migrationsAwaited;
  private final LifecycleDispatcher<T> lifecycle;
  private final SqlExecutor sqlExecutor;
  private final String entityClassName;
  private final Set<String> columnLowerNames;

  public SqlRepository(Class<T> entityClass, RepositoryContext context) {
    this(entityClass, context, null);
  }

  public SqlRepository(
      Class<T> entityClass, RepositoryContext context, SessionCacheProvider session) {
    Objects.requireNonNull(entityClass, "entityClass");
    Objects.requireNonNull(context, "context");
    this.metadata = context.scanner().scan(entityClass);
    this.databaseConnection = session != null ? session.connection() : context.connection();
    this.dialect = context.dialect();
    this.typeMapper = context.typeMapper();
    this.idResolver = context.idResolver();
    this.entityMapper = new EntityMapper<>(entityClass, typeMapper, this.idResolver);
    this.executor = context.executor();
    this.identityMap =
        session != null
            ? IdentityMap.sessionScoped(session, entityClass)
            : IdentityMap.local(entityClass, context.useCache());
    this.migrationsFuture = context.migrationsFuture();
    this.lifecycle = new LifecycleDispatcher<>(entityClass);
    this.sqlExecutor = new SqlExecutor(this.databaseConnection);
    this.entityClassName = entityClass.getSimpleName();
    this.columnLowerNames =
        this.metadata.columns().stream()
            .map(com.github.paperorm.mapping.ColumnMetadata::columnName)
            .map(String::toLowerCase)
            .collect(Collectors.toUnmodifiableSet());
  }

  // -------------------- sync CRUD --------------------

  @Override
  public void ensureTable() {
    awaitMigrations();
    new SchemaManager(this.databaseConnection, this.dialect).ensureTable(this.metadata);
  }

  @Override
  public void save(T entity) {
    Objects.requireNonNull(entity, ENTITY_NULL_MSG);
    this.lifecycle.firePrePersist(entity);
    var idColumn = this.metadata.idColumn();
    var isAuto = idColumn.autoIncrement();

    awaitMigrations();
    this.sqlExecutor.executeVoid(
        errorMsg("save"),
        connection -> {
          var sql = this.dialect.insert(this.metadata);
          try (var statement =
              connection.prepareStatement(
                  sql, isAuto ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
            bindInsertParameters(statement, entity);
            if (statement.executeUpdate() == 0) {
              throw new OrmException("Failed to save, no rows affected");
            }
            if (isAuto) {
              assignGeneratedKey(statement, idColumn, entity);
            }
          }
        });
    updateIdentityMapAfterWrite(entity, idColumn);
  }

  private void updateIdentityMapAfterWrite(
      T entity, com.github.paperorm.mapping.ColumnMetadata idColumn) {
    var idValue = this.entityMapper.readField(idColumn, entity);
    if (idValue == null) {
      return;
    }
    if (this.databaseConnection.isInTransaction()) {
      this.identityMap.evict(idValue);
      return;
    }
    this.identityMap.register(idValue, entity);
  }

  @Override
  public void saveAll(Iterable<T> entities) {
    Objects.requireNonNull(entities, "Entities cannot be null");
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
    this.sqlExecutor.executeVoid(
        errorMsg(SAVE_ALL),
        connection -> {
          var sql = this.dialect.insert(this.metadata);
          try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (var entity : entities) {
              bindInsertParameters(statement, entity);
              if (statement.executeUpdate() == 0) {
                throw new OrmException(errorMsg(SAVE_ALL) + ", no rows affected");
              }
              try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                  throw new OrmException(errorMsg(SAVE_ALL) + ", no generated key returned");
                }
                var generatedId = keys.getObject(1);
                this.entityMapper.setGeneratedId(idColumn, entity, generatedId);
              }
            }
          }
        });

    for (var entity : entities) {
      updateIdentityMapAfterWrite(entity, idColumn);
    }
  }

  private void saveAllBatch(List<T> entities) {
    awaitMigrations();
    this.sqlExecutor.executeVoid(
        errorMsg(SAVE_ALL),
        connection -> {
          var sql = this.dialect.insert(this.metadata);
          try (var statement = connection.prepareStatement(sql, Statement.NO_GENERATED_KEYS)) {
            for (var entity : entities) {
              bindInsertParameters(statement, entity);
              statement.addBatch();
            }
            statement.executeBatch();
          }
        });

    var idColumn = this.metadata.idColumn();
    for (var entity : entities) {
      updateIdentityMapAfterWrite(entity, idColumn);
    }
  }

  @Override
  public void update(T entity) {
    Objects.requireNonNull(entity, ENTITY_NULL_MSG);
    this.lifecycle.firePreUpdate(entity);
    var idColumn = this.metadata.idColumn();
    var idValue = this.entityMapper.readField(idColumn, entity);
    if (idValue == null) {
      throw new IllegalArgumentException("Cannot update entity with null ID");
    }

    awaitMigrations();
    this.sqlExecutor.executeVoid(
        errorMsg("update", idValue),
        connection -> {
          var sql = this.dialect.update(this.metadata);
          if (sql == null) {
            return;
          }
          try (var statement = connection.prepareStatement(sql)) {
            bindUpdateParameters(statement, entity, idValue);
            if (statement.executeUpdate() == 0) {
              throw new OrmException("Failed to update, no rows affected");
            }
          }
        });

    updateIdentityMapAfterWrite(entity, idColumn);
  }

  @Override
  public void updateAll(Iterable<T> entities) {
    Objects.requireNonNull(entities, "Entities cannot be null");
    var idColumn = this.metadata.idColumn();
    var updates = prepareUpdates(entities, idColumn);
    if (updates.isEmpty()) {
      return;
    }

    awaitMigrations();
    this.sqlExecutor.executeVoid(
        errorMsg("updateAll"),
        connection -> {
          var sql = this.dialect.update(this.metadata);
          if (sql == null) {
            return;
          }
          try (var statement = connection.prepareStatement(sql)) {
            for (var update : updates) {
              bindUpdateParameters(statement, cast(update.entity()), update.idValue());
              statement.addBatch();
            }
            statement.executeBatch();
          }
        });

    for (var update : updates) {
      updateIdentityMapAfterWrite(cast(update.entity()), idColumn);
    }
  }

  private List<Update> prepareUpdates(
      Iterable<T> entities, com.github.paperorm.mapping.ColumnMetadata idColumn) {
    var updates = new ArrayList<Update>();
    for (var entity : entities) {
      if (entity == null) {
        continue;
      }
      this.lifecycle.firePreUpdate(entity);
      var idValue = this.entityMapper.readField(idColumn, entity);
      if (idValue == null) {
        throw new IllegalArgumentException("Entity must have a non-null ID to be updated");
      }
      updates.add(new Update(entity, idValue));
    }
    return updates;
  }

  private record Update(Object entity, Object idValue) {}

  @Override
  public void delete(T entity) {
    Objects.requireNonNull(entity, ENTITY_NULL_MSG);
    this.lifecycle.firePreDelete(entity);
    var idValue = this.entityMapper.readField(this.metadata.idColumn(), entity);
    if (idValue == null) {
      throw new IllegalArgumentException("Cannot delete entity with null ID");
    }
    performDelete(idValue);
    this.identityMap.evict(idValue);
  }

  @Override
  public void deleteById(Object id) {
    Objects.requireNonNull(id, ID_NULL_MSG);
    var cached = this.identityMap.resolve(id);
    if (cached != null) {
      this.lifecycle.firePreDelete(cached);
    } else {
      loadAndFirePreDelete(id);
    }

    performDelete(id);
    this.identityMap.evict(id);
  }

  private void loadAndFirePreDelete(Object id) {
    var loaded = loadEntityById(id);
    if (loaded != null) {
      this.lifecycle.firePreDelete(loaded);
    }
  }

  private T loadEntityById(Object id) {
    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("loadEntityById", id),
        connection -> {
          var sql = this.dialect.selectById(this.metadata);
          try (var statement = connection.prepareStatement(sql)) {
            this.typeMapper.setParameter(statement, 1, id);
            try (var resultSet = statement.executeQuery()) {
              if (!resultSet.next()) {
                return null;
              }
              return this.entityMapper.mapRow(resultSet, this.metadata.columns());
            }
          }
        });
  }

  private void performDelete(Object id) {
    awaitMigrations();
    this.sqlExecutor.executeVoid(
        errorMsg("deleteById", id),
        connection -> {
          var sql = this.dialect.deleteById(this.metadata);
          try (var statement = connection.prepareStatement(sql)) {
            this.typeMapper.setParameter(statement, 1, id);
            statement.executeUpdate();
          }
        });
  }

  // -------------------- sync queries --------------------

  @Override
  public Optional<T> findById(Object id) {
    Objects.requireNonNull(id, ID_NULL_MSG);
    var cached = this.identityMap.resolve(id);
    if (cached != null) {
      this.lifecycle.firePostLoad(cached);
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
                return Optional.empty();
              }
              var entity = this.entityMapper.mapRow(resultSet, this.metadata.columns());
              var cachedOrNew = cacheOrGet(entity);
              this.lifecycle.firePostLoad(cachedOrNew);
              return Optional.of(cachedOrNew);
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
    Objects.requireNonNull(column, "Column cannot be null");
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
    Objects.requireNonNull(id, ID_NULL_MSG);
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
    var validated = validateWhereClause(whereClause);

    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("findByQuery"),
        connection -> {
          var sql = this.dialect.selectAllWithCondition(this.metadata, validated);
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
    var validated = validateWhereClause(whereClause);

    awaitMigrations();
    return this.sqlExecutor.execute(
        errorMsg("countByQuery"),
        connection -> {
          var sql = this.dialect.countWithCondition(this.metadata, validated);
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
    return Spec.fromRepository(this, this.dialect);
  }

  // -------------------- cache --------------------

  @Override
  public void clearCache() {
    this.identityMap.clear();
  }

  // -------------------- private helpers --------------------

  private String errorMsg(String operation, Object... id) {
    var msg = "Failed to " + operation + " " + this.entityClassName;
    if (id != null && id.length > 0 && id[0] != null) {
      msg += " with id " + id[0];
    }
    return msg;
  }

  private String errorMsg(String operation) {
    return "Failed to " + operation + " " + this.entityClassName;
  }

  @SuppressWarnings("unchecked")
  private T cast(Object object) {
    return (T) object;
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
      if (!keys.next()) {
        throw new OrmException("Failed to save, no generated key returned");
      }
      var generatedId = keys.getObject(1);
      this.entityMapper.setGeneratedId(idColumn, entity, generatedId);
    }
  }

  private String validateWhereClause(String whereClause) {
    if (whereClause == null) {
      return null;
    }
    var trimmed = whereClause.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.contains(";")) {
      throw new IllegalArgumentException(
          "Raw where clauses cannot contain statement separators (';')");
    }
    if (trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
      throw new IllegalArgumentException("Raw where clauses cannot contain SQL comments");
    }
    return trimmed;
  }

  private List<T> mapResultSet(ResultSet resultSet) throws SQLException {
    var entities = new ArrayList<T>();
    var columns = this.metadata.columns();
    while (resultSet.next()) {
      var entity = this.entityMapper.mapRow(resultSet, columns);
      var cachedOrNew = cacheOrGet(entity);
      this.lifecycle.firePostLoad(cachedOrNew);
      entities.add(cachedOrNew);
    }
    return entities;
  }

  private void validateColumnExists(String column) {
    if (!this.columnLowerNames.contains(column.toLowerCase())) {
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
