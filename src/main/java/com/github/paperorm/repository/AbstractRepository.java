package com.github.paperorm.repository;

import com.github.paperorm.PaperOrm;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractRepository<T> implements Repository<T> {

  private final Repository<T> delegate;

  protected AbstractRepository(Class<T> entityClass, PaperOrm orm) {
    this.delegate = orm.getRepository(entityClass);
  }

  @Override
  public void ensureTable() {
    delegate.ensureTable();
  }

  @Override
  public CompletableFuture<Void> ensureTableAsync() {
    return delegate.ensureTableAsync();
  }

  @Override
  public void save(T entity) {
    delegate.save(entity);
  }

  @Override
  public CompletableFuture<Void> saveAsync(T entity) {
    return delegate.saveAsync(entity);
  }

  @Override
  public void update(T entity) {
    delegate.update(entity);
  }

  @Override
  public CompletableFuture<Void> updateAsync(T entity) {
    return delegate.updateAsync(entity);
  }

  @Override
  public void deleteById(Object id) {
    delegate.deleteById(id);
  }

  @Override
  public CompletableFuture<Void> deleteByIdAsync(Object id) {
    return delegate.deleteByIdAsync(id);
  }

  @Override
  public Optional<T> findById(Object id) {
    return delegate.findById(id);
  }

  @Override
  public CompletableFuture<Optional<T>> findByIdAsync(Object id) {
    return delegate.findByIdAsync(id);
  }

  @Override
  public List<T> findAll() {
    return delegate.findAll();
  }

  @Override
  public CompletableFuture<List<T>> findAllAsync() {
    return delegate.findAllAsync();
  }

  @Override
  public List<T> findBy(String column, Object value) {
    return delegate.findBy(column, value);
  }

  @Override
  public CompletableFuture<List<T>> findByAsync(String column, Object value) {
    return delegate.findByAsync(column, value);
  }

  @Override
  public boolean existsById(Object id) {
    return delegate.existsById(id);
  }

  @Override
  public CompletableFuture<Boolean> existsByIdAsync(Object id) {
    return delegate.existsByIdAsync(id);
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    return delegate.findByQuery(whereClause, parameters);
  }

  @Override
  public CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters) {
    return delegate.findByQueryAsync(whereClause, parameters);
  }

  @Override
  public Query<T> select() {
    return delegate.select();
  }

  @Override
  public void clearCache() {
    delegate.clearCache();
  }
}
