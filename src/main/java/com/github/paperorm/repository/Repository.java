package com.github.paperorm.repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Repository<T> {

  void ensureTable();

  void save(T entity);

  void update(T entity);

  void deleteById(Object id);

  Optional<T> findById(Object id);

  List<T> findAll();

  List<T> findBy(String column, Object value);

  boolean existsById(Object id);

  CompletableFuture<Void> ensureTableAsync();

  CompletableFuture<Void> saveAsync(T entity);

  CompletableFuture<Void> updateAsync(T entity);

  CompletableFuture<Void> deleteByIdAsync(Object id);

  CompletableFuture<Optional<T>> findByIdAsync(Object id);

  CompletableFuture<List<T>> findAllAsync();

  CompletableFuture<List<T>> findByAsync(String column, Object value);

  CompletableFuture<Boolean> existsByIdAsync(Object id);

  List<T> findByQuery(String whereClause, Object... parameters);

  CompletableFuture<List<T>> findByQueryAsync(String whereClause, Object... parameters);

  void clearCache();

  Query<T> select();
}
