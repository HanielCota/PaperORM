package com.github.paperorm.repository;

import com.github.paperorm.repository.query.Specification;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class AsyncRepository<T> extends ForwardingRepository<T> {

  private final Executor executor;

  public AsyncRepository(Repository<T> delegate, Executor executor) {
    super(delegate);
    this.executor = executor;
  }

  @Override
  public CompletableFuture<Void> ensureTableAsync() {
    return CompletableFuture.runAsync(delegate::ensureTable, executor);
  }

  @Override
  public CompletableFuture<Void> saveAsync(T entity) {
    return CompletableFuture.runAsync(() -> delegate.save(entity), executor);
  }

  @Override
  public CompletableFuture<Void> saveAllAsync(Iterable<T> entities) {
    return CompletableFuture.runAsync(() -> delegate.saveAll(entities), executor);
  }

  @Override
  public CompletableFuture<Void> updateAsync(T entity) {
    return CompletableFuture.runAsync(() -> delegate.update(entity), executor);
  }

  @Override
  public CompletableFuture<Void> updateAllAsync(Iterable<T> entities) {
    return CompletableFuture.runAsync(() -> delegate.updateAll(entities), executor);
  }

  @Override
  public CompletableFuture<Void> deleteAsync(T entity) {
    return CompletableFuture.runAsync(() -> delegate.delete(entity), executor);
  }

  @Override
  public CompletableFuture<Void> deleteByIdAsync(Object id) {
    return CompletableFuture.runAsync(() -> delegate.deleteById(id), executor);
  }

  @Override
  public CompletableFuture<Optional<T>> findByIdAsync(Object id) {
    return CompletableFuture.supplyAsync(() -> delegate.findById(id), executor);
  }

  @Override
  public CompletableFuture<List<T>> findAllAsync() {
    return CompletableFuture.supplyAsync(delegate::findAll, executor);
  }

  @Override
  public CompletableFuture<List<T>> findByAsync(String column, Object value) {
    return CompletableFuture.supplyAsync(() -> delegate.findBy(column, value), executor);
  }

  @Override
  public CompletableFuture<Boolean> existsByIdAsync(Object id) {
    return CompletableFuture.supplyAsync(() -> delegate.existsById(id), executor);
  }

  @Override
  public CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters) {
    return CompletableFuture.supplyAsync(
        () -> delegate.findByQuery(whereClause, parameters), executor);
  }

  @Override
  public CompletableFuture<Long> countByQueryAsync(String whereClause, Object... parameters) {
    return CompletableFuture.supplyAsync(
        () -> delegate.countByQuery(whereClause, parameters), executor);
  }

  @Override
  public CompletableFuture<List<T>> findAsync(Specification<T> spec) {
    return CompletableFuture.supplyAsync(() -> delegate.find(spec), executor);
  }
}
