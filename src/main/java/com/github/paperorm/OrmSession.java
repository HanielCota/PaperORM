package com.github.paperorm;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.database.TransactionCallback;
import com.github.paperorm.database.VoidTransactionCallback;
import com.github.paperorm.repository.Repository;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class OrmSession implements AutoCloseable {

  private final DatabaseConnection connection;
  private final OrmFactory factory;
  @Getter private final boolean useCache;
  private final Map<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();
  private final Map<Class<?>, Map<Object, WeakReference<?>>> cacheMap = new ConcurrentHashMap<>();

  public DatabaseConnection connection() {
    return this.connection;
  }

  @SuppressWarnings("unchecked")
  public <T> Repository<T> getRepository(Class<T> entityClass) {
    return (Repository<T>)
        this.repositories.computeIfAbsent(
            entityClass, clazz -> this.factory.createRepository(clazz, this));
  }

  @SuppressWarnings("unchecked")
  public <T> Map<Object, WeakReference<T>> getCache(Class<T> entityClass) {
    var rawMap = this.cacheMap.computeIfAbsent(entityClass, clazz -> new ConcurrentHashMap<>());
    return (Map<Object, WeakReference<T>>) (Map<?, ?>) rawMap;
  }

  public void clearCache() {
    this.cacheMap.clear();
  }

  public <T> T runInTransaction(TransactionCallback<T> callback) {
    try {
      return this.connection.runInTransaction(callback);
    } catch (RuntimeException exception) {
      clearCache();
      throw exception;
    }
  }

  public void runInTransaction(VoidTransactionCallback callback) {
    try {
      this.connection.runInTransaction(callback);
    } catch (RuntimeException exception) {
      clearCache();
      throw exception;
    }
  }

  public <T> java.util.concurrent.CompletableFuture<T> runInTransactionAsync(
      TransactionCallback<T> callback) {
    return java.util.concurrent.CompletableFuture.supplyAsync(
        () -> runInTransaction(callback), this.factory.executor());
  }

  public java.util.concurrent.CompletableFuture<Void> runInTransactionAsync(
      VoidTransactionCallback callback) {
    return java.util.concurrent.CompletableFuture.runAsync(
        () -> runInTransaction(callback), this.factory.executor());
  }

  @Override
  public void close() {
    clearCache();
    this.repositories.clear();
  }
}
