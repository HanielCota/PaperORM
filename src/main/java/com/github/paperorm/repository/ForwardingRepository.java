package com.github.paperorm.repository;

import com.github.paperorm.repository.query.Query;
import com.github.paperorm.repository.query.Specification;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ForwardingRepository<T> implements Repository<T> {

  protected final Repository<T> delegate;

  protected ForwardingRepository(Repository<T> delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public void ensureTable() {
    delegate.ensureTable();
  }

  @Override
  public void save(T entity) {
    delegate.save(entity);
  }

  @Override
  public void saveAll(Iterable<T> entities) {
    delegate.saveAll(entities);
  }

  @Override
  public void update(T entity) {
    delegate.update(entity);
  }

  @Override
  public void updateAll(Iterable<T> entities) {
    delegate.updateAll(entities);
  }

  @Override
  public void delete(T entity) {
    delegate.delete(entity);
  }

  @Override
  public void deleteById(Object id) {
    delegate.deleteById(id);
  }

  @Override
  public Optional<T> findById(Object id) {
    return delegate.findById(id);
  }

  @Override
  public List<T> findAll() {
    return delegate.findAll();
  }

  @Override
  public List<T> findBy(String column, Object value) {
    return delegate.findBy(column, value);
  }

  @Override
  public boolean existsById(Object id) {
    return delegate.existsById(id);
  }

  @Override
  public CompletableFuture<Void> ensureTableAsync() {
    return delegate.ensureTableAsync();
  }

  @Override
  public CompletableFuture<Void> saveAsync(T entity) {
    return delegate.saveAsync(entity);
  }

  @Override
  public CompletableFuture<Void> saveAllAsync(Iterable<T> entities) {
    return delegate.saveAllAsync(entities);
  }

  @Override
  public CompletableFuture<Void> updateAsync(T entity) {
    return delegate.updateAsync(entity);
  }

  @Override
  public CompletableFuture<Void> updateAllAsync(Iterable<T> entities) {
    return delegate.updateAllAsync(entities);
  }

  @Override
  public CompletableFuture<Void> deleteAsync(T entity) {
    return delegate.deleteAsync(entity);
  }

  @Override
  public CompletableFuture<Void> deleteByIdAsync(Object id) {
    return delegate.deleteByIdAsync(id);
  }

  @Override
  public CompletableFuture<Optional<T>> findByIdAsync(Object id) {
    return delegate.findByIdAsync(id);
  }

  @Override
  public CompletableFuture<List<T>> findAllAsync() {
    return delegate.findAllAsync();
  }

  @Override
  public CompletableFuture<List<T>> findByAsync(String column, Object value) {
    return delegate.findByAsync(column, value);
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
  public long countByQuery(String whereClause, Object... parameters) {
    return delegate.countByQuery(whereClause, parameters);
  }

  @Override
  public CompletableFuture<Long> countByQueryAsync(String whereClause, Object... parameters) {
    return delegate.countByQueryAsync(whereClause, parameters);
  }

  @Override
  public void clearCache() {
    delegate.clearCache();
  }

  @Override
  public List<T> find(Specification<T> spec) {
    return delegate.find(spec);
  }

  @Override
  public CompletableFuture<List<T>> findAsync(Specification<T> spec) {
    return delegate.findAsync(spec);
  }

  @Override
  public Query<T> select() {
    return delegate.select();
  }
}
