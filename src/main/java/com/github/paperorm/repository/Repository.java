package com.github.paperorm.repository;

import com.github.paperorm.repository.query.Query;
import com.github.paperorm.repository.query.Specification;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Core CRUD repository interface for PaperORM entities.
 *
 * <p>Provides synchronous and asynchronous operations for persisting, querying, and deleting
 * entities. Implementations handle SQL generation, type mapping, lifecycle callbacks
 * ({@code @PrePersist}, {@code @PostLoad}, etc.), and identity-map caching.
 *
 * <p>Async variants execute on a configurable {@link java.util.concurrent.Executor} (by default
 * virtual threads or ForkJoinPool) and return {@link CompletableFuture}.
 *
 * @param <T> the entity type managed by this repository
 */
public interface Repository<T> {

  /** Creates the database table for this entity if it does not already exist. */
  void ensureTable();

  /**
   * Persists a new entity. For auto-increment IDs, the generated ID is set back on the entity.
   *
   * @param entity the entity to persist (must not be null)
   */
  void save(T entity);

  /**
   * Persists multiple entities in a batch. For auto-increment IDs, generated IDs are set on each
   * entity individually.
   *
   * @param entities the entities to persist
   */
  void saveAll(Iterable<T> entities);

  /**
   * Updates an existing entity identified by its primary key.
   *
   * @param entity the entity to update (must have a non-null ID)
   * @throws IllegalArgumentException if the entity has a null ID
   * @throws com.github.paperorm.exception.OrmException if no row was updated (entity not found)
   */
  void update(T entity);

  /**
   * Updates multiple entities in a batch. Each entity must have a non-null ID.
   *
   * @param entities the entities to update
   */
  void updateAll(Iterable<T> entities);

  /**
   * Deletes an entity by its primary key value.
   *
   * @param entity the entity to delete
   */
  void delete(T entity);

  /**
   * Deletes an entity by its primary key value.
   *
   * @param id the primary key value (must not be null)
   */
  void deleteById(Object id);

  /**
   * Finds an entity by its primary key.
   *
   * @param id the primary key value (must not be null)
   * @return an {@link Optional} containing the entity, or empty if not found
   */
  Optional<T> findById(Object id);

  /**
   * Retrieves all entities from the table.
   *
   * @return a list of all entities (never null, may be empty)
   */
  List<T> findAll();

  /**
   * Finds entities by a single column value.
   *
   * @param column the column name (as defined in the entity, not the database column name)
   * @param value the value to match
   * @return a list of matching entities (never null, may be empty)
   */
  List<T> findBy(String column, Object value);

  /**
   * Checks whether an entity with the given primary key exists.
   *
   * @param id the primary key value
   * @return {@code true} if an entity with this ID exists
   */
  boolean existsById(Object id);

  /** Async variant of {@link #ensureTable()}. */
  CompletableFuture<Void> ensureTableAsync();

  /** Async variant of {@link #save(Object)}. */
  CompletableFuture<Void> saveAsync(T entity);

  /** Async variant of {@link #saveAll(Iterable)}. */
  CompletableFuture<Void> saveAllAsync(Iterable<T> entities);

  /** Async variant of {@link #update(Object)}. */
  CompletableFuture<Void> updateAsync(T entity);

  /** Async variant of {@link #updateAll(Iterable)}. */
  CompletableFuture<Void> updateAllAsync(Iterable<T> entities);

  /** Async variant of {@link #delete(Object)}. */
  CompletableFuture<Void> deleteAsync(T entity);

  /** Async variant of {@link #deleteById(Object)}. */
  CompletableFuture<Void> deleteByIdAsync(Object id);

  /** Async variant of {@link #findById(Object)}. */
  CompletableFuture<Optional<T>> findByIdAsync(Object id);

  /** Async variant of {@link #findAll()}. */
  CompletableFuture<List<T>> findAllAsync();

  /** Async variant of {@link #findBy(String, Object)}. */
  CompletableFuture<List<T>> findByAsync(String column, Object value);

  /** Async variant of {@link #existsById(Object)}. */
  CompletableFuture<Boolean> existsByIdAsync(Object id);

  /**
   * Executes a raw SQL WHERE clause against the entity table. Parameters are bound positionally
   * using prepared statements.
   *
   * <p><strong>Warning:</strong> the {@code whereClause} is concatenated directly into the SQL.
   * Only use this method with trusted input. Prefer {@link #find(Specification)} or {@link
   * #select()} for safe query building.
   *
   * @param whereClause the SQL WHERE clause (e.g., {@code "name = ? AND count > ?"})
   * @param parameters positional parameter values
   * @return a list of matching entities
   */
  List<T> findByQuery(String whereClause, Object... parameters);

  /** Async variant of {@link #findByQuery(String, Object...)}. */
  CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters);

  /**
   * Counts entities matching a raw SQL WHERE clause.
   *
   * @param whereClause the SQL WHERE clause
   * @param parameters positional parameter values
   * @return the number of matching entities
   */
  long countByQuery(String whereClause, Object... parameters);

  /** Async variant of {@link #countByQuery(String, Object...)}. */
  CompletableFuture<Long> countByQueryAsync(String whereClause, Object... parameters);

  /** Clears the identity-map cache, forcing subsequent reads to fetch from the database. */
  void clearCache();

  /**
   * Creates a fluent query builder for this entity type.
   *
   * @return a new {@link Query} instance
   */
  Query<T> select();

  /**
   * Finds entities using a {@link Specification}.
   *
   * @param spec the specification defining the query criteria
   * @return a list of matching entities
   */
  List<T> find(Specification<T> spec);

  /** Async variant of {@link #find(Specification)}. */
  CompletableFuture<List<T>> findAsync(Specification<T> spec);
}
