package com.github.paperorm.repository;

public interface SessionCacheProvider {
  boolean isUseCache();

  <T> java.util.Map<Object, T> getCache(Class<T> entityClass);

  com.github.paperorm.database.DatabaseConnection connection();

  <T> T getIdentity(Class<T> entityClass, Object id);

  <T> void registerIdentity(Class<T> entityClass, Object id, T entity);

  void evictIdentity(Class<?> entityClass, Object id);
}
