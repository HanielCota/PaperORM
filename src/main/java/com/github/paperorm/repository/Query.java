package com.github.paperorm.repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Query<T> {

  Query<T> where(String column);

  Query<T> and(String column);

  Query<T> or(String column);

  Query<T> eq(Object value);

  Query<T> notEq(Object value);

  Query<T> greaterThan(Object value);

  Query<T> lessThan(Object value);

  Query<T> like(Object value);

  Query<T> isNull();

  Query<T> isNotNull();

  Query<T> in(Object... values);

  Query<T> in(java.util.Collection<?> values);

  Query<T> orderBy(String column, String direction);

  Query<T> limit(int limit);

  List<T> list();

  CompletableFuture<List<T>> listAsync();

  Optional<T> uniqueResult();

  CompletableFuture<Optional<T>> uniqueResultAsync();
}
