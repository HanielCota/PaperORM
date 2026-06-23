package com.github.paperorm.repository.query;

import com.github.paperorm.dialect.SqlDialect;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class Spec<T> implements Specification<T> {

  private final List<Fragment> fragments = new ArrayList<>();
  private String currentColumn;

  private Spec() {}

  Spec(List<Fragment> fragments) {
    this.fragments.addAll(fragments);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static <T> Spec<T> empty() {
    return new Spec();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  Spec<T> copy() {
    var fragments = new ArrayList<>(this.fragments);
    Spec spec = new Spec(fragments);
    spec.currentColumn = this.currentColumn;
    return spec;
  }

  public static <T> Spec<T> where(String column) {
    Objects.requireNonNull(column, "column");
    var spec = new Spec<T>();
    spec.currentColumn = column;
    return spec;
  }

  Spec<T> whereInstance(String column) {
    Objects.requireNonNull(column, "column");
    this.currentColumn = column;
    return this;
  }

  public Spec<T> and(String column) {
    Objects.requireNonNull(column, "column");
    flushColumn();
    fragments.add(new Junction("AND"));
    this.currentColumn = column;
    return this;
  }

  public Spec<T> or(String column) {
    Objects.requireNonNull(column, "column");
    flushColumn();
    fragments.add(new Junction("OR"));
    this.currentColumn = column;
    return this;
  }

  public Spec<T> eq(Object value) {
    return addOp("=", value);
  }

  public Spec<T> notEq(Object value) {
    return addOp("<>", value);
  }

  public Spec<T> greaterThan(Object value) {
    return addOp(">", value);
  }

  public Spec<T> lessThan(Object value) {
    return addOp("<", value);
  }

  public Spec<T> greaterOrEqual(Object value) {
    return addOp(">=", value);
  }

  public Spec<T> lessOrEqual(Object value) {
    return addOp("<=", value);
  }

  public Spec<T> like(Object value) {
    return addOp("LIKE", value);
  }

  public Spec<T> isNull() {
    requireColumn();
    fragments.add(new NullCondition(currentColumn, true));
    currentColumn = null;
    return this;
  }

  public Spec<T> isNotNull() {
    requireColumn();
    fragments.add(new NullCondition(currentColumn, false));
    currentColumn = null;
    return this;
  }

  public Spec<T> in(Object... values) {
    requireColumn();
    fragments.add(new InCondition(currentColumn, List.of(values)));
    currentColumn = null;
    return this;
  }

  public Spec<T> in(Collection<?> values) {
    requireColumn();
    fragments.add(new InCondition(currentColumn, List.copyOf(values)));
    currentColumn = null;
    return this;
  }

  public Spec<T> orderBy(String column, String direction) {
    flushColumn();
    var dir = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
    fragments.add(new OrderByClause(column, dir));
    return this;
  }

  public Spec<T> limit(int limit) {
    flushColumn();
    fragments.add(new LimitClause(limit));
    return this;
  }

  public Spec<T> offset(int offset) {
    flushColumn();
    fragments.add(new OffsetClause(offset));
    return this;
  }

  private Spec<T> addOp(String operator, Object value) {
    requireColumn();
    fragments.add(new ColumnCondition(currentColumn, operator, value));
    currentColumn = null;
    return this;
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

  @Override
  public String toSql(SqlDialect dialect) {
    flushColumn();
    var sb = new StringBuilder();
    for (var f : fragments) {
      switch (f) {
        case ColumnCondition(var col, var op, var val) -> {
          if (!sb.isEmpty()) sb.append(' ');
          sb.append(String.format("%s %s ?", dialect.quoteIdentifier(col), op));
        }
        case Junction(var kw) -> sb.append(' ').append(kw);
        case NullCondition(var col, var isNull) -> {
          if (!sb.isEmpty()) sb.append(' ');
          sb.append(dialect.quoteIdentifier(col)).append(isNull ? " IS NULL" : " IS NOT NULL");
        }
        case InCondition(var col, var vals) -> {
          if (!sb.isEmpty()) sb.append(' ');
          sb.append(dialect.quoteIdentifier(col)).append(" IN (");
          var sep = "";
          for (var ignored : vals) {
            sb.append(sep).append('?');
            sep = ", ";
          }
          sb.append(')');
        }
        case OrderByClause(var col, var dir) ->
            sb.append(String.format(" ORDER BY %s %s", dialect.quoteIdentifier(col), dir));
        case LimitClause(var lim) -> sb.append(String.format(" LIMIT %d", lim));
        case OffsetClause(var off) -> sb.append(String.format(" OFFSET %d", off));
      }
    }
    return sb.toString();
  }

  @Override
  public List<Object> getParameters() {
    flushColumn();
    var result = new ArrayList<>();
    for (var f : fragments) {
      switch (f) {
        case ColumnCondition(var col, var op, Object val) -> result.add(val);
        case InCondition(var col, List<?> vals) -> result.addAll(vals);
        case Junction ignored -> {}
        case NullCondition ignored -> {}
        case OrderByClause ignored -> {}
        case LimitClause ignored -> {}
        case OffsetClause ignored -> {}
      }
    }
    return result;
  }

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
