package com.github.paperorm;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.database.TransactionCallback;
import com.github.paperorm.database.VoidTransactionCallback;
import com.github.paperorm.repository.Repository;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class OrmSession implements AutoCloseable {

  private final DatabaseConnection connection;
  private final OrmFactory factory;
  @Getter private final boolean useCache;
  private final Map<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();
  private final Map<Class<?>, Map<Object, Object>> cacheMap = new ConcurrentHashMap<>();
  private volatile boolean closed = false;

  public DatabaseConnection connection() {
    return this.connection;
  }

  @SuppressWarnings("unchecked")
  public <T> Repository<T> getRepository(Class<T> entityClass) {
    Objects.requireNonNull(entityClass, "entityClass");
    if (this.closed) {
      throw new IllegalStateException("Session is closed");
    }
    return (Repository<T>)
        this.repositories.computeIfAbsent(
            entityClass, clazz -> this.factory.createRepository(clazz, this));
  }

  @SuppressWarnings("unchecked")
  public <T> Map<Object, T> getCache(Class<T> entityClass) {
    var rawMap = this.cacheMap.computeIfAbsent(entityClass, clazz -> new ConcurrentHashMap<>());
    return (Map<Object, T>) rawMap;
  }

  @SuppressWarnings("unchecked")
  public <T> T getIdentity(Class<T> entityClass, Object id) {
    var cache = this.cacheMap.get(entityClass);
    if (cache == null) return null;
    return (T) cache.get(id);
  }

  public <T> void registerIdentity(Class<T> entityClass, Object id, T entity) {
    getCache(entityClass).put(id, entity);
  }

  public void evictIdentity(Class<?> entityClass, Object id) {
    var cache = this.cacheMap.get(entityClass);
    if (cache != null) {
      cache.remove(id);
    }
  }

  public void clearIdentityMap() {
    this.cacheMap.clear();
  }

  public <T> T runInTransaction(TransactionCallback<T> callback) {
    Objects.requireNonNull(callback, "callback");
    try {
      var result = this.connection.runInTransaction(callback);
      clearIdentityMap();
      return result;
    } catch (RuntimeException exception) {
      clearIdentityMap();
      throw exception;
    }
  }

  public void runInTransaction(VoidTransactionCallback callback) {
    Objects.requireNonNull(callback, "callback");
    try {
      this.connection.runInTransaction(callback);
      clearIdentityMap();
    } catch (RuntimeException exception) {
      clearIdentityMap();
      throw exception;
    }
  }

  public <T> CompletableFuture<T> runInTransactionAsync(TransactionCallback<T> callback) {
    Objects.requireNonNull(callback, "callback");
    return CompletableFuture.supplyAsync(() -> runInTransaction(callback), this.factory.executor());
  }

  public CompletableFuture<Void> runInTransactionAsync(VoidTransactionCallback callback) {
    Objects.requireNonNull(callback, "callback");
    return CompletableFuture.runAsync(() -> runInTransaction(callback), this.factory.executor());
  }

  @Override
  public void close() {
    this.closed = true;
    clearIdentityMap();
    this.repositories.clear();
  }
}
