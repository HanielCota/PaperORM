package com.github.paperorm.repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoggingRepository<T> implements Repository<T> {

  private final Repository<T> delegate;
  private final Logger logger;

  public LoggingRepository(Repository<T> delegate) {
    this(delegate, Logger.getLogger(delegate.getClass().getName()));
  }

  public LoggingRepository(Repository<T> delegate, Logger logger) {
    this.delegate = delegate;
    this.logger = logger;
  }

  @Override
  public void ensureTable() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("ensureTable()");
    }
    delegate.ensureTable();
  }

  @Override
  public void save(T entity) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("save(%s)", entity));
    }
    delegate.save(entity);
  }

  @Override
  public void update(T entity) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("update(%s)", entity));
    }
    delegate.update(entity);
  }

  @Override
  public void delete(T entity) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("delete(%s)", entity));
    }
    delegate.delete(entity);
  }

  @Override
  public void deleteById(Object id) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("deleteById(%s)", id));
    }
    delegate.deleteById(id);
  }

  @Override
  public Optional<T> findById(Object id) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("findById(%s)", id));
    }
    return delegate.findById(id);
  }

  @Override
  public List<T> findAll() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("findAll()");
    }
    return delegate.findAll();
  }

  @Override
  public List<T> findBy(String column, Object value) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("findBy(%s, %s)", column, value));
    }
    return delegate.findBy(column, value);
  }

  @Override
  public boolean existsById(Object id) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("existsById(%s)", id));
    }
    return delegate.existsById(id);
  }

  @Override
  public void clearCache() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("clearCache()");
    }
    delegate.clearCache();
  }

  @Override
  public Query<T> select() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("select()");
    }
    return delegate.select();
  }

  @Override
  public List<T> find(Specification<T> spec) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("find(%s)", spec));
    }
    return delegate.find(spec);
  }

  @Override
  public CompletableFuture<Void> ensureTableAsync() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("ensureTableAsync()");
    }
    return delegate.ensureTableAsync();
  }

  @Override
  public CompletableFuture<Void> saveAsync(T entity) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("saveAsync(%s)", entity));
    }
    return delegate.saveAsync(entity);
  }

  @Override
  public CompletableFuture<Void> updateAsync(T entity) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("updateAsync(%s)", entity));
    }
    return delegate.updateAsync(entity);
  }

  @Override
  public CompletableFuture<Void> deleteAsync(T entity) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("deleteAsync(%s)", entity));
    }
    return delegate.deleteAsync(entity);
  }

  @Override
  public CompletableFuture<Void> deleteByIdAsync(Object id) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("deleteByIdAsync(%s)", id));
    }
    return delegate.deleteByIdAsync(id);
  }

  @Override
  public CompletableFuture<Optional<T>> findByIdAsync(Object id) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("findByIdAsync(%s)", id));
    }
    return delegate.findByIdAsync(id);
  }

  @Override
  public CompletableFuture<List<T>> findAllAsync() {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("findAllAsync()");
    }
    return delegate.findAllAsync();
  }

  @Override
  public CompletableFuture<List<T>> findByAsync(String column, Object value) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("findByAsync(%s, %s)", column, value));
    }
    return delegate.findByAsync(column, value);
  }

  @Override
  public CompletableFuture<Boolean> existsByIdAsync(Object id) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("existsByIdAsync(%s)", id));
    }
    return delegate.existsByIdAsync(id);
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("findByQuery(%s)", whereClause));
    }
    return delegate.findByQuery(whereClause, parameters);
  }

  @Override
  public CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("findByQueryAsync(%s)", whereClause));
    }
    return delegate.findByQueryAsync(whereClause, parameters);
  }

  @Override
  public CompletableFuture<List<T>> findAsync(Specification<T> spec) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(() -> String.format("findAsync(%s)", spec));
    }
    return delegate.findAsync(spec);
  }
}
