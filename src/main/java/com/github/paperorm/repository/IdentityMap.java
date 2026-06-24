package com.github.paperorm.repository;

import com.github.paperorm.OrmSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IdentityMap<T> {

  private final Map<Object, T> localCache;
  private final OrmSession session;
  private final Class<T> entityClass;
  private final boolean enabled;

  private IdentityMap(OrmSession session, Class<T> entityClass, boolean useCache) {
    this.session = session;
    this.entityClass = entityClass;

    if (session == null) {
      this.enabled = useCache;
      this.localCache = new ConcurrentHashMap<>();
      return;
    }

    this.enabled = session.isUseCache();
    this.localCache = session.getCache(entityClass);
  }

  public static <T> IdentityMap<T> sessionScoped(OrmSession session, Class<T> entityClass) {
    return new IdentityMap<>(session, entityClass, true);
  }

  public static <T> IdentityMap<T> local(Class<T> entityClass, boolean useCache) {
    return new IdentityMap<>(null, entityClass, useCache);
  }

  public T cacheOrGet(Object id, T entity) {
    if (!this.enabled) {
      return entity;
    }

    var existing = resolve(id);
    if (existing != null) {
      return existing;
    }

    register(id, entity);
    return entity;
  }

  public T resolve(Object id) {
    if (!this.enabled) {
      return null;
    }

    if (this.session != null) {
      return this.session.getIdentity(this.entityClass, id);
    }

    return this.localCache.get(id);
  }

  public void register(Object id, T entity) {
    if (!this.enabled) {
      return;
    }

    if (this.session != null) {
      this.session.registerIdentity(this.entityClass, id, entity);
      return;
    }

    this.localCache.put(id, entity);
  }

  public void evict(Object id) {
    if (!this.enabled) {
      return;
    }

    if (this.session != null) {
      this.session.evictIdentity(this.entityClass, id);
      return;
    }

    this.localCache.remove(id);
  }

  public void clear() {
    this.localCache.clear();
  }
}
