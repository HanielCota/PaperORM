package com.github.paperorm.repository.query;

import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.repository.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public final class SqlQuery<T> implements Query<T> {

  private final Repository<T> repository;
  private final SqlDialect dialect;
  private final String whereClause;
  private final List<Object> parameters;
  private final String currentColumn;
  private final int limit;
  private final int offset;

  public SqlQuery(Repository<T> repository, SqlDialect dialect) {
    this(repository, dialect, "", List.of(), null, -1, -1);
  }

  private SqlQuery(
      Repository<T> repository,
      SqlDialect dialect,
      String whereClause,
      List<Object> parameters,
      String currentColumn,
      int limit,
      int offset) {
    this.repository = repository;
    this.dialect = dialect;
    this.whereClause = whereClause;
    this.parameters = parameters;
    this.currentColumn = currentColumn;
    this.limit = limit;
    this.offset = offset;
  }

  @Override
  public Query<T> where(String column) {
    Objects.requireNonNull(column, "column");
    if (!this.whereClause.isEmpty()) {
      throw new IllegalStateException("Use and() or or() to chain multiple conditions");
    }
    return new SqlQuery<>(
        this.repository,
        this.dialect,
        this.whereClause,
        this.parameters,
        column,
        this.limit,
        this.offset);
  }

  @Override
  public Query<T> and(String column) {
    Objects.requireNonNull(column, "column");
    if (this.currentColumn != null) {
      throw new IllegalStateException(
          "Previous column '"
              + this.currentColumn
              + "' has no operator. Call eq(), notEq(), isNull(), etc. before and().");
    }
    return new SqlQuery<>(
        this.repository,
        this.dialect,
        this.whereClause + " AND ",
        this.parameters,
        column,
        this.limit,
        this.offset);
  }

  @Override
  public Query<T> or(String column) {
    Objects.requireNonNull(column, "column");
    if (this.currentColumn != null) {
      throw new IllegalStateException(
          "Previous column '"
              + this.currentColumn
              + "' has no operator. Call eq(), notEq(), isNull(), etc. before or().");
    }
    return new SqlQuery<>(
        this.repository,
        this.dialect,
        this.whereClause + " OR ",
        this.parameters,
        column,
        this.limit,
        this.offset);
  }

  private Query<T> addOperator(String operator, Object value) {
    if (this.currentColumn == null) {
      throw new IllegalStateException(
          "No active column defined. Call where(), and(), or or() first.");
    }
    var quotedColumn = this.dialect.quoteIdentifier(this.currentColumn);
    var newClause = this.whereClause + quotedColumn + " " + operator + " ?";
    var newParams = new ArrayList<>(this.parameters);
    newParams.add(value);
    return new SqlQuery<>(
        this.repository, this.dialect, newClause, newParams, null, this.limit, this.offset);
  }

  @Override
  public Query<T> eq(Object value) {
    return addOperator("=", value);
  }

  @Override
  public Query<T> notEq(Object value) {
    return addOperator("<>", value);
  }

  @Override
  public Query<T> greaterThan(Object value) {
    return addOperator(">", value);
  }

  @Override
  public Query<T> lessThan(Object value) {
    return addOperator("<", value);
  }

  @Override
  public Query<T> like(Object value) {
    return addOperator("LIKE", value);
  }

  @Override
  public Query<T> isNull() {
    if (this.currentColumn == null) {
      throw new IllegalStateException(
          "No active column defined. Call where(), and(), or or() first.");
    }
    var quotedColumn = this.dialect.quoteIdentifier(this.currentColumn);
    var newClause = this.whereClause + quotedColumn + " IS NULL";
    return new SqlQuery<>(
        this.repository, this.dialect, newClause, this.parameters, null, this.limit, this.offset);
  }

  @Override
  public Query<T> isNotNull() {
    if (this.currentColumn == null) {
      throw new IllegalStateException(
          "No active column defined. Call where(), and(), or or() first.");
    }
    var quotedColumn = this.dialect.quoteIdentifier(this.currentColumn);
    var newClause = this.whereClause + quotedColumn + " IS NOT NULL";
    return new SqlQuery<>(
        this.repository, this.dialect, newClause, this.parameters, null, this.limit, this.offset);
  }

  @Override
  public Query<T> in(Object... values) {
    return in(java.util.Arrays.asList(values));
  }

  @Override
  public Query<T> in(java.util.Collection<?> values) {
    if (this.currentColumn == null) {
      throw new IllegalStateException(
          "No active column defined. Call where(), and(), or or() first.");
    }
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("Values for IN clause cannot be null or empty");
    }
    var quotedColumn = this.dialect.quoteIdentifier(this.currentColumn);

    var placeholders = new StringJoiner(", ");
    var newParams = new ArrayList<>(this.parameters);
    for (var val : values) {
      placeholders.add("?");
      newParams.add(val);
    }

    var newClause = this.whereClause + quotedColumn + " IN (" + placeholders + ")";
    return new SqlQuery<>(
        this.repository, this.dialect, newClause, newParams, null, this.limit, this.offset);
  }

  @Override
  public Query<T> orderBy(String column, String direction) {
    Objects.requireNonNull(column, "column");
    var isDesc = "DESC".equalsIgnoreCase(direction);
    var dir = isDesc ? "DESC" : "ASC";
    var quotedColumn = this.dialect.quoteIdentifier(column);
    var newClause = this.whereClause + " ORDER BY " + quotedColumn + " " + dir;
    return new SqlQuery<>(
        this.repository,
        this.dialect,
        newClause,
        this.parameters,
        this.currentColumn,
        this.limit,
        this.offset);
  }

  @Override
  public Query<T> limit(int limit) {
    return new SqlQuery<>(
        this.repository,
        this.dialect,
        this.whereClause,
        this.parameters,
        this.currentColumn,
        limit,
        this.offset);
  }

  @Override
  public Query<T> offset(int offset) {
    return new SqlQuery<>(
        this.repository,
        this.dialect,
        this.whereClause,
        this.parameters,
        this.currentColumn,
        this.limit,
        offset);
  }

  @Override
  public List<T> list() {
    validateState();
    var sql = buildSql();
    var params = this.parameters.toArray();
    return this.repository.findByQuery(sql, params);
  }

  @Override
  public CompletableFuture<List<T>> listAsync() {
    validateState();
    var sql = buildSql();
    var params = this.parameters.toArray();
    return this.repository.findByQueryAsync(sql, params);
  }

  @Override
  public Optional<T> uniqueResult() {
    var limitedQuery = this.limit(1);
    var result = limitedQuery.list();
    if (result.isEmpty()) {
      return Optional.empty();
    }
    var first = result.getFirst();
    return Optional.of(first);
  }

  @Override
  public CompletableFuture<Optional<T>> uniqueResultAsync() {
    var limitedQuery = this.limit(1);
    return limitedQuery
        .listAsync()
        .thenApply(
            result -> {
              if (result.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(result.getFirst());
            });
  }

  @Override
  public long count() {
    validateState();
    var sql = this.whereClause;
    var params = this.parameters.toArray();
    return this.repository.countByQuery(sql, params);
  }

  @Override
  public CompletableFuture<Long> countAsync() {
    validateState();
    var params = this.parameters.toArray();
    return this.repository.countByQueryAsync(this.whereClause, params);
  }

  private String buildSql() {
    var sb = new StringBuilder(this.whereClause);
    if (this.limit >= 0) {
      sb.append(" LIMIT ").append(this.limit);
    }
    if (this.offset >= 0) {
      sb.append(" OFFSET ").append(this.offset);
    }
    return sb.toString();
  }

  private void validateState() {
    if (this.currentColumn != null) {
      throw new IllegalStateException(
          "Column '"
              + this.currentColumn
              + "' has no operator. Call eq(), notEq(), isNull(), etc. before executing the query.");
    }
  }
}
