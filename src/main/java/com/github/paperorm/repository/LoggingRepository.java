package com.github.paperorm.repository;

import com.github.paperorm.repository.query.Specification;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoggingRepository<T> extends ForwardingRepository<T> {

  private final Logger logger;

  public LoggingRepository(Repository<T> delegate) {
    this(delegate, null);
  }

  public LoggingRepository(Repository<T> delegate, Logger logger) {
    super(delegate);
    this.logger = logger != null ? logger : Logger.getLogger(delegate.getClass().getName());
  }

  @Override
  public void save(T entity) {
    log("save({0})", entity);
    delegate.save(entity);
  }

  @Override
  public void saveAll(Iterable<T> entities) {
    log("saveAll(Iterable)");
    delegate.saveAll(entities);
  }

  @Override
  public void update(T entity) {
    log("update({0})", entity);
    delegate.update(entity);
  }

  @Override
  public void updateAll(Iterable<T> entities) {
    log("updateAll(Iterable)");
    delegate.updateAll(entities);
  }

  @Override
  public void delete(T entity) {
    log("delete({0})", entity);
    delegate.delete(entity);
  }

  @Override
  public void deleteById(Object id) {
    log("deleteById({0})", id);
    delegate.deleteById(id);
  }

  @Override
  public Optional<T> findById(Object id) {
    log("findById({0})", id);
    return delegate.findById(id);
  }

  @Override
  public List<T> findAll() {
    log("findAll()");
    return delegate.findAll();
  }

  @Override
  public List<T> findBy(String column, Object value) {
    log("findBy({0}, {1})", column, value);
    return delegate.findBy(column, value);
  }

  @Override
  public boolean existsById(Object id) {
    log("existsById({0})", id);
    return delegate.existsById(id);
  }

  @Override
  public List<T> find(Specification<T> spec) {
    log("find({0})", spec);
    return delegate.find(spec);
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    log("findByQuery({0})", whereClause);
    return delegate.findByQuery(whereClause, parameters);
  }

  @Override
  public long countByQuery(String whereClause, Object... parameters) {
    log("countByQuery({0})", whereClause);
    return delegate.countByQuery(whereClause, parameters);
  }

  @Override
  public void clearCache() {
    log("clearCache()");
    delegate.clearCache();
  }

  private void log(String format, Object... args) {
    if (logger.isLoggable(Level.FINE)) {
      logger.log(Level.FINE, format.formatted(args));
    }
  }
}
