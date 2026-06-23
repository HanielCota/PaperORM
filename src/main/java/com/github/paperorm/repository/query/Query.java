package com.github.paperorm.repository.query;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Fluent query builder for constructing type-safe SQL queries.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * var results = repository.select()
 *     .where("name").eq("John")
 *     .and("age").greaterThan(18)
 *     .orderBy("name", "ASC")
 *     .limit(10)
 *     .list();
 * }</pre>
 *
 * <p>Each {@code where()}, {@code and()}, or {@code or()} call must be followed by an operator
 * ({@code eq()}, {@code notEq()}, {@code isNull()}, etc.) before another condition or terminal
 * operation is called.
 *
 * @param <T> the entity type
 */
public interface Query<T> {

  /** Starts a new WHERE condition on the given column. */
  Query<T> where(String column);

  /** Adds an AND condition on the given column. */
  Query<T> and(String column);

  /** Adds an OR condition on the given column. */
  Query<T> or(String column);

  /** Adds an equals comparison. */
  Query<T> eq(Object value);

  /** Adds a not-equals comparison. */
  Query<T> notEq(Object value);

  /** Adds a greater-than comparison. */
  Query<T> greaterThan(Object value);

  /** Adds a less-than comparison. */
  Query<T> lessThan(Object value);

  /** Adds a LIKE comparison. */
  Query<T> like(Object value);

  /** Adds an IS NULL check. */
  Query<T> isNull();

  /** Adds an IS NOT NULL check. */
  Query<T> isNotNull();

  /** Adds an IN clause with the given values. */
  Query<T> in(Object... values);

  /** Adds an IN clause with the given collection. */
  Query<T> in(java.util.Collection<?> values);

  /**
   * Adds an ORDER BY clause.
   *
   * @param column the column name
   * @param direction "ASC" or "DESC" (case-insensitive)
   */
  Query<T> orderBy(String column, String direction);

  /** Adds a LIMIT clause. */
  Query<T> limit(int limit);

  /** Adds an OFFSET clause. */
  Query<T> offset(int offset);

  /** Executes the query and returns the matching entities. */
  List<T> list();

  /** Async variant of {@link #list()}. */
  CompletableFuture<List<T>> listAsync();

  /**
   * Executes the query and returns a single result, if any. A LIMIT 1 is automatically appended.
   */
  Optional<T> uniqueResult();

  /** Async variant of {@link #uniqueResult()}. */
  CompletableFuture<Optional<T>> uniqueResultAsync();

  /** Executes the query and returns the count of matching rows. */
  long count();

  /** Async variant of {@link #count()}. */
  CompletableFuture<Long> countAsync();
}
