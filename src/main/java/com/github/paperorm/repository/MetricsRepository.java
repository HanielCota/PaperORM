package com.github.paperorm.repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MetricsRepository<T> implements Repository<T> {

  private final Repository<T> delegate;
  private final AtomicLong saveCount = new AtomicLong();
  private final AtomicLong updateCount = new AtomicLong();
  private final AtomicLong findCount = new AtomicLong();
  private final AtomicLong deleteCount = new AtomicLong();
  private final AtomicLong queryCount = new AtomicLong();
  private final AtomicLong totalTimeNanos = new AtomicLong();

  public MetricsRepository(Repository<T> delegate) {
    this.delegate = delegate;
  }

  public long saveCount() {
    return saveCount.get();
  }

  public long updateCount() {
    return updateCount.get();
  }

  public long findCount() {
    return findCount.get();
  }

  public long deleteCount() {
    return deleteCount.get();
  }

  public long queryCount() {
    return queryCount.get();
  }

  public long totalTimeMillis() {
    return totalTimeNanos.get() / 1_000_000;
  }

  public void reset() {
    saveCount.set(0);
    updateCount.set(0);
    findCount.set(0);
    deleteCount.set(0);
    queryCount.set(0);
    totalTimeNanos.set(0);
  }

  @Override
  public void ensureTable() {
    queryCount.incrementAndGet();
    timed(delegate::ensureTable);
  }

  @Override
  public void save(T entity) {
    saveCount.incrementAndGet();
    timed(() -> delegate.save(entity));
  }

  @Override
  public void update(T entity) {
    updateCount.incrementAndGet();
    timed(() -> delegate.update(entity));
  }

  @Override
  public void delete(T entity) {
    deleteCount.incrementAndGet();
    timed(() -> delegate.delete(entity));
  }

  @Override
  public void deleteById(Object id) {
    deleteCount.incrementAndGet();
    timed(() -> delegate.deleteById(id));
  }

  @Override
  public Optional<T> findById(Object id) {
    findCount.incrementAndGet();
    return timed(delegate::findById, id);
  }

  @Override
  public List<T> findAll() {
    queryCount.incrementAndGet();
    return timed(delegate::findAll);
  }

  @Override
  public List<T> findBy(String column, Object value) {
    queryCount.incrementAndGet();
    return timed(() -> delegate.findBy(column, value));
  }

  @Override
  public boolean existsById(Object id) {
    queryCount.incrementAndGet();
    return timed(() -> delegate.existsById(id));
  }

  @Override
  public void clearCache() {
    timed(delegate::clearCache);
  }

  @Override
  public Query<T> select() {
    return delegate.select();
  }

  @Override
  public List<T> find(Specification<T> spec) {
    queryCount.incrementAndGet();
    return timed(() -> delegate.find(spec));
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    queryCount.incrementAndGet();
    return timed(() -> delegate.findByQuery(whereClause, parameters));
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
  public CompletableFuture<Void> updateAsync(T entity) {
    return delegate.updateAsync(entity);
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
  public CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters) {
    return delegate.findByQueryAsync(whereClause, parameters);
  }

  @Override
  public CompletableFuture<List<T>> findAsync(Specification<T> spec) {
    return delegate.findAsync(spec);
  }

  // ---- timing helpers ----
  private void timed(Runnable action) {
    var start = System.nanoTime();
    try {
      action.run();
    } finally {
      totalTimeNanos.addAndGet(System.nanoTime() - start);
    }
  }

  private <R> R timed(Supplier<R> action) {
    var start = System.nanoTime();
    try {
      return action.get();
    } finally {
      totalTimeNanos.addAndGet(System.nanoTime() - start);
    }
  }

  private <R> R timed(Function<Object, R> action, Object arg) {
    var start = System.nanoTime();
    try {
      return action.apply(arg);
    } finally {
      totalTimeNanos.addAndGet(System.nanoTime() - start);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "MetricsRepository{saves=%d, updates=%d, finds=%d, deletes=%d, queries=%d, totalTimeMs=%d}",
        saveCount(), updateCount(), findCount(), deleteCount(), queryCount(), totalTimeMillis());
  }
}
