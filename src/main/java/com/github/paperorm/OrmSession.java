package com.github.paperorm;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.database.TransactionCallback;
import com.github.paperorm.repository.Repository;
import com.github.paperorm.repository.SessionCacheProvider;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class OrmSession implements AutoCloseable, SessionCacheProvider {

  private static final String ENTITY_CLASS_NULL_MSG = "entityClass cannot be null";

  private final OrmContext context;
  private final OrmFactory factory;
  private final Map<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();
  private final Map<Class<?>, Map<Object, Object>> cacheMap = new ConcurrentHashMap<>();
  private volatile boolean closed = false;

  public DatabaseConnection connection() {
    return this.context.connection();
  }

  public boolean isUseCache() {
    return this.context.useCache();
  }

  public <T> Repository<T> getRepository(Class<T> entityClass) {
    Objects.requireNonNull(entityClass, ENTITY_CLASS_NULL_MSG);
    if (this.closed) {
      throw new IllegalStateException("Session is closed");
    }

    return cast(
        this.repositories.computeIfAbsent(
            entityClass, clazz -> this.factory.createRepository(clazz, this)));
  }

  public <T> Map<Object, T> getCache(Class<T> entityClass) {
    var rawMap = this.cacheMap.computeIfAbsent(entityClass, clazz -> new ConcurrentHashMap<>());
    return cast(rawMap);
  }

  public <T> T getIdentity(Class<T> entityClass, Object id) {
    Objects.requireNonNull(entityClass, ENTITY_CLASS_NULL_MSG);
    var cache = this.cacheMap.get(entityClass);
    if (cache == null) {
      return null;
    }

    return cast(cache.get(id));
  }

  public <T> void registerIdentity(Class<T> entityClass, Object id, T entity) {
    getCache(entityClass).put(id, entity);
  }

  public void evictIdentity(Class<?> entityClass, Object id) {
    Objects.requireNonNull(entityClass, ENTITY_CLASS_NULL_MSG);
    var cache = this.cacheMap.get(entityClass);
    if (cache == null) {
      return;
    }

    cache.remove(id);
  }

  public void clearIdentityMap() {
    this.cacheMap.clear();
  }

  public <T> T runInTransaction(TransactionCallback<T> callback) {
    Objects.requireNonNull(callback, "callback cannot be null");
    return this.context.connection().runInTransaction(callback);
  }

  public <T> CompletableFuture<T> runInTransactionAsync(TransactionCallback<T> callback) {
    Objects.requireNonNull(callback, "callback cannot be null");
    return CompletableFuture.supplyAsync(() -> runInTransaction(callback), this.context.executor());
  }

  @Override
  public void close() {
    this.closed = true;
    clearIdentityMap();
    this.repositories.clear();
  }

  @SuppressWarnings("unchecked")
  private static <T> T cast(Object object) {
    return (T) object;
  }
}
