package com.github.paperorm.repository;

import com.github.paperorm.dialect.SqlDialect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SqlQuery<T> implements Query<T> {

  private final Repository<T> repository;
  private final SqlDialect dialect;
  private final StringBuilder whereClause = new StringBuilder();
  private final List<Object> parameters = new ArrayList<>();
  private boolean hasCondition = false;
  private String currentColumn = null;

  public SqlQuery(Repository<T> repository, SqlDialect dialect) {
    this.repository = repository;
    this.dialect = dialect;
  }

  @Override
  public Query<T> where(String column) {
    if (this.hasCondition) {
      throw new IllegalStateException("Use and() or or() to chain multiple conditions");
    }
    this.currentColumn = column;
    return this;
  }

  @Override
  public Query<T> and(String column) {
    this.whereClause.append(" AND ");
    this.currentColumn = column;
    return this;
  }

  @Override
  public Query<T> or(String column) {
    this.whereClause.append(" OR ");
    this.currentColumn = column;
    return this;
  }

  private Query<T> addOperator(String operator, Object value) {
    if (this.currentColumn == null) {
      throw new IllegalStateException(
          "No active column defined. Call where(), and(), or or() first.");
    }
    var quotedColumn = this.dialect.quoteIdentifier(this.currentColumn);

    this.whereClause.append(quotedColumn).append(" ").append(operator).append(" ?");

    this.parameters.add(value);
    this.currentColumn = null;
    this.hasCondition = true;
    return this;
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

    this.whereClause.append(quotedColumn).append(" IS NULL");
    this.currentColumn = null;
    this.hasCondition = true;
    return this;
  }

  @Override
  public Query<T> isNotNull() {
    if (this.currentColumn == null) {
      throw new IllegalStateException(
          "No active column defined. Call where(), and(), or or() first.");
    }
    var quotedColumn = this.dialect.quoteIdentifier(this.currentColumn);

    this.whereClause.append(quotedColumn).append(" IS NOT NULL");
    this.currentColumn = null;
    this.hasCondition = true;
    return this;
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

    var placeholders = new java.util.StringJoiner(", ");
    for (var val : values) {
      placeholders.add("?");
      this.parameters.add(val);
    }

    this.whereClause.append(quotedColumn).append(" IN (").append(placeholders).append(")");
    this.currentColumn = null;
    this.hasCondition = true;
    return this;
  }

  @Override
  public Query<T> orderBy(String column, String direction) {
    var isDesc = "DESC".equalsIgnoreCase(direction);
    var dir = isDesc ? "DESC" : "ASC";
    var quotedColumn = this.dialect.quoteIdentifier(column);

    this.whereClause.append(" ORDER BY ").append(quotedColumn).append(" ").append(dir);
    return this;
  }

  @Override
  public Query<T> limit(int limit) {
    this.whereClause.append(" LIMIT ").append(limit);
    return this;
  }

  @Override
  public Query<T> offset(int offset) {
    this.whereClause.append(" OFFSET ").append(offset);
    return this;
  }

  @Override
  public List<T> list() {
    var sql = this.whereClause.toString();
    var params = this.parameters.toArray();
    return this.repository.findByQuery(sql, params);
  }

  @Override
  public CompletableFuture<List<T>> listAsync() {
    var sql = this.whereClause.toString();
    var params = this.parameters.toArray();
    return this.repository.findByQueryAsync(sql, params);
  }

  @Override
  public Optional<T> uniqueResult() {
    var result = list();
    if (result.isEmpty()) {
      return Optional.empty();
    }
    var first = result.getFirst();
    return Optional.of(first);
  }

  @Override
  public CompletableFuture<Optional<T>> uniqueResultAsync() {
    return listAsync()
        .thenApply(
            list -> {
              if (list.isEmpty()) {
                return Optional.empty();
              }
              var first = list.getFirst();
              return Optional.of(first);
            });
  }
}
