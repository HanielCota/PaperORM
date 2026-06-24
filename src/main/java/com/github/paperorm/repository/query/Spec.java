package com.github.paperorm.repository.query;

import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.repository.Repository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class Spec<T> implements Specification<T>, Query<T> {

  private static final String COLUMN_REQUIRED = "column";

  private final List<Fragment> fragments;
  private final String currentColumn;
  private final Repository<T> repository;
  private final SqlDialect dialect;

  private Spec(List<Fragment> fragments, String currentColumn) {
    this.fragments = List.copyOf(fragments);
    this.currentColumn = currentColumn;
    this.repository = null;
    this.dialect = null;
  }

  private Spec(
      List<Fragment> fragments,
      String currentColumn,
      Repository<T> repository,
      SqlDialect dialect) {
    this.fragments = List.copyOf(fragments);
    this.currentColumn = currentColumn;
    this.repository = repository;
    this.dialect = dialect;
  }

  public static <T> Spec<T> of(String column) {
    Objects.requireNonNull(column, COLUMN_REQUIRED);
    return new Spec<>(List.of(), column);
  }

  static <T> Spec<T> empty() {
    return new Spec<>(List.of(), null);
  }

  public static <T> Spec<T> fromRepository(Repository<T> repository, SqlDialect dialect) {
    return new Spec<>(List.of(), null, repository, dialect);
  }

  private Spec<T> withFragment(Fragment fragment, String newColumn) {
    var newFragments = new ArrayList<>(this.fragments);
    newFragments.add(fragment);
    return new Spec<>(newFragments, newColumn, this.repository, this.dialect);
  }

  // -- Query interface (fluent builders) --

  @Override
  public Spec<T> where(String column) {
    Objects.requireNonNull(column, COLUMN_REQUIRED);
    return new Spec<>(List.of(), column, this.repository, this.dialect);
  }

  @Override
  public Spec<T> and(String column) {
    Objects.requireNonNull(column, COLUMN_REQUIRED);
    flushColumn();
    return withFragment(new Junction("AND"), column);
  }

  @Override
  public Spec<T> or(String column) {
    Objects.requireNonNull(column, COLUMN_REQUIRED);
    flushColumn();
    return withFragment(new Junction("OR"), column);
  }

  @Override
  public Spec<T> eq(Object value) {
    if (value == null) {
      return isNull();
    }
    return addOp("=", value);
  }

  @Override
  public Spec<T> notEq(Object value) {
    if (value == null) {
      return isNotNull();
    }
    return addOp("<>", value);
  }

  @Override
  public Spec<T> greaterThan(Object value) {
    return addOp(">", value);
  }

  @Override
  public Spec<T> lessThan(Object value) {
    return addOp("<", value);
  }

  @Override
  public Spec<T> greaterOrEqual(Object value) {
    return addOp(">=", value);
  }

  @Override
  public Spec<T> lessOrEqual(Object value) {
    return addOp("<=", value);
  }

  @Override
  public Spec<T> like(Object value) {
    return addOp("LIKE", value);
  }

  @Override
  public Spec<T> isNull() {
    requireColumn();
    return withFragment(new NullCondition(currentColumn, true), null);
  }

  @Override
  public Spec<T> isNotNull() {
    requireColumn();
    return withFragment(new NullCondition(currentColumn, false), null);
  }

  @Override
  public Spec<T> in(Object... values) {
    requireColumn();
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("IN clause values cannot be null or empty");
    }
    return withFragment(new InCondition(currentColumn, List.of(values)), null);
  }

  @Override
  public Spec<T> in(Collection<?> values) {
    requireColumn();
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("IN clause values cannot be null or empty");
    }
    return withFragment(new InCondition(currentColumn, List.copyOf(values)), null);
  }

  @Override
  public Spec<T> orderBy(String column, String direction) {
    flushColumn();
    var dir = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
    return withFragment(new OrderByClause(column, dir), null);
  }

  @Override
  public Spec<T> limit(int limit) {
    flushColumn();
    return withFragment(new LimitClause(limit), null);
  }

  @Override
  public Spec<T> offset(int offset) {
    flushColumn();
    return withFragment(new OffsetClause(offset), null);
  }

  // -- Terminal operations --

  @Override
  public List<T> list() {
    var sql = toSql();
    var params = getParameters();
    return this.repository.findByQuery(sql, params.toArray());
  }

  @Override
  public CompletableFuture<List<T>> listAsync() {
    var sql = toSql();
    var params = getParameters();
    return this.repository.findByQueryAsync(sql, params.toArray());
  }

  @Override
  public Optional<T> uniqueResult() {
    var result = limit(1).list();
    return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
  }

  @Override
  public CompletableFuture<Optional<T>> uniqueResultAsync() {
    return limit(1)
        .listAsync()
        .thenApply(result -> result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst()));
  }

  @Override
  public long count() {
    var sql = toSql();
    var params = getParameters();
    return this.repository.countByQuery(sql, params.toArray());
  }

  @Override
  public CompletableFuture<Long> countAsync() {
    var sql = toSql();
    var params = getParameters();
    return this.repository.countByQueryAsync(sql, params.toArray());
  }

  // -- Specification interface --

  @Override
  public String toSql(SqlDialect dialect) {
    flushColumn();
    var sb = new StringBuilder();
    for (var f : fragments) {
      appendFragment(sb, f, dialect);
    }
    return sb.toString();
  }

  private void appendFragment(StringBuilder sb, Fragment f, SqlDialect dialect) {
    switch (f) {
      case ColumnCondition(var col, var op, var val) ->
          appendWithSpace(sb, dialect.quoteIdentifier(col) + " " + op + " ?");
      case Junction(var kw) -> sb.append(' ').append(kw);
      case NullCondition(var col, var isNull) ->
          appendWithSpace(
              sb, dialect.quoteIdentifier(col) + (isNull ? " IS NULL" : " IS NOT NULL"));
      case InCondition(var col, var vals) -> {
        appendWithSpace(sb, dialect.quoteIdentifier(col) + " IN (");
        var sep = "";
        for (var ignored : vals) {
          sb.append(sep).append('?');
          sep = ", ";
        }
        sb.append(')');
      }
      case OrderByClause(var col, var dir) ->
          sb.append(" ORDER BY ").append(dialect.quoteIdentifier(col)).append(' ').append(dir);
      case LimitClause(var lim) -> sb.append(" LIMIT ").append(lim);
      case OffsetClause(var off) -> sb.append(" OFFSET ").append(off);
    }
  }

  private static void appendWithSpace(StringBuilder sb, String fragment) {
    if (!sb.isEmpty()) {
      sb.append(' ');
    }
    sb.append(fragment);
  }

  private String toSql() {
    return toSql(this.dialect);
  }

  @Override
  public List<Object> getParameters() {
    flushColumn();
    var result = new ArrayList<>();
    for (var f : fragments) {
      switch (f) {
        case ColumnCondition(var col, var op, Object val) -> result.add(val);
        case InCondition(var col, List<?> vals) -> result.addAll(vals);
        default -> {
          // Junction, NullCondition, OrderByClause, LimitClause, OffsetClause
          // contribute no parameters
        }
      }
    }
    return result;
  }

  // -- Internal helpers --

  private Spec<T> addOp(String operator, Object value) {
    requireColumn();
    return withFragment(new ColumnCondition(currentColumn, operator, value), null);
  }

  private void flushColumn() {
    if (currentColumn != null) {
      throw new IllegalStateException(
          "Column '" + currentColumn + "' has no operator. Call eq(), notEq(), isNull(), etc.");
    }
  }

  private void requireColumn() {
    if (currentColumn == null) {
      throw new IllegalStateException("No active column. Call where(), and(), or or() first.");
    }
  }

  // -- Override Specification defaults to return Spec instead of anonymous Specification --

  @Override
  public Specification<T> and(Specification<T> other) {
    return new AndSpecification<>(this, other);
  }

  @Override
  public Specification<T> or(Specification<T> other) {
    return new OrSpecification<>(this, other);
  }

  @Override
  public Specification<T> not() {
    return new NotSpecification<>(this);
  }

  // -- Fragment types --

  private sealed interface Fragment {}

  private record ColumnCondition(String column, String operator, Object value)
      implements Fragment {}

  private record Junction(String keyword) implements Fragment {}

  private record NullCondition(String column, boolean isNull) implements Fragment {}

  private record InCondition(String column, List<?> values) implements Fragment {}

  private record OrderByClause(String column, String direction) implements Fragment {}

  private record LimitClause(int limit) implements Fragment {}

  private record OffsetClause(int offset) implements Fragment {}
}
