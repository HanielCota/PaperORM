package com.github.paperorm.repository;

import com.github.paperorm.PaperOrm;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An abstract base repository class that delegates all Repository operations to an active PaperOrm
 * instance repository.
 *
 * <p>Developers using PaperORM can extend this class to implement custom repositories with
 * domain-specific methods (e.g., finding entities by unique non-primary keys like UUID or name).
 *
 * @param <T> The entity type
 */
public abstract class AbstractRepository<T> implements Repository<T> {

  protected final Repository<T> delegate;

  protected AbstractRepository(Class<T> entityClass, PaperOrm orm) {
    if (orm == null) {
      throw new IllegalArgumentException("PaperOrm instance cannot be null");
    }
    this.delegate = orm.getRepository(entityClass);
  }

  @Override
  public void ensureTable() {
    this.delegate.ensureTable();
  }

  @Override
  public void save(T entity) {
    this.delegate.save(entity);
  }

  @Override
  public void update(T entity) {
    this.delegate.update(entity);
  }

  @Override
  public void deleteById(Object id) {
    this.delegate.deleteById(id);
  }

  @Override
  public Optional<T> findById(Object id) {
    return this.delegate.findById(id);
  }

  @Override
  public List<T> findAll() {
    return this.delegate.findAll();
  }

  @Override
  public List<T> findBy(String column, Object value) {
    return this.delegate.findBy(column, value);
  }

  @Override
  public boolean existsById(Object id) {
    return this.delegate.existsById(id);
  }

  @Override
  public CompletableFuture<Void> ensureTableAsync() {
    return this.delegate.ensureTableAsync();
  }

  @Override
  public CompletableFuture<Void> saveAsync(T entity) {
    return this.delegate.saveAsync(entity);
  }

  @Override
  public CompletableFuture<Void> updateAsync(T entity) {
    return this.delegate.updateAsync(entity);
  }

  @Override
  public CompletableFuture<Void> deleteByIdAsync(Object id) {
    return this.delegate.deleteByIdAsync(id);
  }

  @Override
  public CompletableFuture<Optional<T>> findByIdAsync(Object id) {
    return this.delegate.findByIdAsync(id);
  }

  @Override
  public CompletableFuture<List<T>> findAllAsync() {
    return this.delegate.findAllAsync();
  }

  @Override
  public CompletableFuture<List<T>> findByAsync(String column, Object value) {
    return this.delegate.findByAsync(column, value);
  }

  @Override
  public CompletableFuture<Boolean> existsByIdAsync(Object id) {
    return this.delegate.existsByIdAsync(id);
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    return this.delegate.findByQuery(whereClause, parameters);
  }

  @Override
  public CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters) {
    return this.delegate.findByQueryAsync(whereClause, parameters);
  }

  @Override
  public void clearCache() {
    this.delegate.clearCache();
  }

  @Override
  public Query<T> select() {
    return this.delegate.select();
  }
}
