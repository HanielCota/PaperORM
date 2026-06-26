package com.github.paperorm.repository;

import com.github.paperorm.repository.query.Specification;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class MetricsRepository<T> extends ForwardingRepository<T> {

  private final AtomicLong saveCount = new AtomicLong();
  private final AtomicLong updateCount = new AtomicLong();
  private final AtomicLong findCount = new AtomicLong();
  private final AtomicLong deleteCount = new AtomicLong();
  private final AtomicLong queryCount = new AtomicLong();
  private final AtomicLong totalTimeNanos = new AtomicLong();
  private volatile boolean enabled = true;

  public MetricsRepository(Repository<T> delegate) {
    super(delegate);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
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
  public void save(T entity) {
    saveCount.incrementAndGet();
    timed(() -> delegate.save(entity));
  }

  @Override
  public void ensureTable() {
    queryCount.incrementAndGet();
    timed(delegate::ensureTable);
  }

  @Override
  public void saveAll(Iterable<T> entities) {
    var count = countEntities(entities);
    saveCount.addAndGet(count);
    timed(() -> delegate.saveAll(entities));
  }

  private long countEntities(Iterable<T> entities) {
    if (entities == null) {
      return 0;
    }
    var count = 0L;
    for (var ignored : entities) {
      count++;
    }
    return count;
  }

  @Override
  public void update(T entity) {
    updateCount.incrementAndGet();
    timed(() -> delegate.update(entity));
  }

  @Override
  public void updateAll(Iterable<T> entities) {
    updateCount.incrementAndGet();
    timed(() -> delegate.updateAll(entities));
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
    return timed(() -> delegate.findById(id));
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
  public long countByQuery(String whereClause, Object... parameters) {
    queryCount.incrementAndGet();
    return timed(() -> delegate.countByQuery(whereClause, parameters));
  }

  @Override
  public void clearCache() {
    timed(delegate::clearCache);
  }

  private void timed(Runnable action) {
    if (!enabled) {
      action.run();
      return;
    }
    var start = System.nanoTime();
    try {
      action.run();
    } finally {
      totalTimeNanos.addAndGet(System.nanoTime() - start);
    }
  }

  private <R> R timed(Supplier<R> action) {
    if (!enabled) {
      return action.get();
    }
    var start = System.nanoTime();
    try {
      return action.get();
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
