package com.github.paperorm.repository;

import com.github.paperorm.repository.query.Query;
import com.github.paperorm.repository.query.Specification;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
  public java.util.concurrent.Executor getExecutor() {
    return delegate.getExecutor();
  }

  @Override
  public List<T> findByQuery(String whereClause, Object... parameters) {
    return delegate.findByQuery(whereClause, parameters);
  }

  @Override
  public long countByQuery(String whereClause, Object... parameters) {
    return delegate.countByQuery(whereClause, parameters);
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
  public Query<T> select() {
    return delegate.select();
  }
}
