package com.github.paperorm.repository.query;

import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.repository.Repository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SqlQuery<T> implements Query<T> {

  private final Repository<T> repository;
  private final SqlDialect dialect;
  private final Spec<T> spec;

  public SqlQuery(Repository<T> repository, SqlDialect dialect) {
    this.repository = repository;
    this.dialect = dialect;
    this.spec = Spec.empty();
  }

  private SqlQuery(Repository<T> repository, SqlDialect dialect, Spec<T> spec) {
    this.repository = repository;
    this.dialect = dialect;
    this.spec = spec;
  }

  @Override
  public Query<T> where(String column) {
    return new SqlQuery<>(repository, dialect, spec.copy().whereInstance(column));
  }

  @Override
  public Query<T> and(String column) {
    return new SqlQuery<>(repository, dialect, spec.copy().and(column));
  }

  @Override
  public Query<T> or(String column) {
    return new SqlQuery<>(repository, dialect, spec.copy().or(column));
  }

  @Override
  public Query<T> eq(Object value) {
    return new SqlQuery<>(repository, dialect, spec.copy().eq(value));
  }

  @Override
  public Query<T> notEq(Object value) {
    return new SqlQuery<>(repository, dialect, spec.copy().notEq(value));
  }

  @Override
  public Query<T> greaterThan(Object value) {
    return new SqlQuery<>(repository, dialect, spec.copy().greaterThan(value));
  }

  @Override
  public Query<T> lessThan(Object value) {
    return new SqlQuery<>(repository, dialect, spec.copy().lessThan(value));
  }

  @Override
  public Query<T> greaterOrEqual(Object value) {
    return new SqlQuery<>(repository, dialect, spec.copy().greaterOrEqual(value));
  }

  @Override
  public Query<T> lessOrEqual(Object value) {
    return new SqlQuery<>(repository, dialect, spec.copy().lessOrEqual(value));
  }

  @Override
  public Query<T> like(Object value) {
    return new SqlQuery<>(repository, dialect, spec.copy().like(value));
  }

  @Override
  public Query<T> isNull() {
    return new SqlQuery<>(repository, dialect, spec.copy().isNull());
  }

  @Override
  public Query<T> isNotNull() {
    return new SqlQuery<>(repository, dialect, spec.copy().isNotNull());
  }

  @Override
  public Query<T> in(Object... values) {
    return new SqlQuery<>(repository, dialect, spec.copy().in(values));
  }

  @Override
  public Query<T> in(java.util.Collection<?> values) {
    return new SqlQuery<>(repository, dialect, spec.copy().in(values));
  }

  @Override
  public Query<T> orderBy(String column, String direction) {
    return new SqlQuery<>(repository, dialect, spec.copy().orderBy(column, direction));
  }

  @Override
  public Query<T> limit(int limit) {
    return new SqlQuery<>(repository, dialect, spec.copy().limit(limit));
  }

  @Override
  public Query<T> offset(int offset) {
    return new SqlQuery<>(repository, dialect, spec.copy().offset(offset));
  }

  @Override
  public List<T> list() {
    return repository.findByQuery(spec.toSql(dialect), spec.getParameters().toArray());
  }

  @Override
  public CompletableFuture<List<T>> listAsync() {
    return repository.findByQueryAsync(spec.toSql(dialect), spec.getParameters().toArray());
  }

  @Override
  public Optional<T> uniqueResult() {
    var result = limit(1).list();
    if (result.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(result.getFirst());
  }

  @Override
  public CompletableFuture<Optional<T>> uniqueResultAsync() {
    return limit(1)
        .listAsync()
        .thenApply(result -> result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst()));
  }

  @Override
  public long count() {
    return repository.countByQuery(spec.toSql(dialect), spec.getParameters().toArray());
  }

  @Override
  public CompletableFuture<Long> countAsync() {
    return repository.countByQueryAsync(spec.toSql(dialect), spec.getParameters().toArray());
  }
}
