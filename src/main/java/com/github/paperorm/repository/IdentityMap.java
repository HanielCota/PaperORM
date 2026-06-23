package com.github.paperorm.repository;

import com.github.paperorm.OrmSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe identity map that ensures each entity is loaded only once per session or per
 * repository instance.
 *
 * <h3>Two operational modes</h3>
 *
 * <p><b>Session mode</b> — when constructed with an {@link OrmSession}, the cache is shared across
 * all repositories within that session. Cache lifecycle is tied to the session: clearing the
 * session clears all entity caches. The session's {@link OrmSession#isUseCache()} setting
 * determines whether caching is enabled; the {@code useCache} constructor parameter is ignored in
 * this mode.
 *
 * <p><b>Local mode</b> — when constructed without a session ({@code null}), the cache is private to
 * this repository instance. The {@code useCache} constructor parameter controls enablement.
 *
 * <h3>Thread safety</h3>
 *
 * <p>All operations are safe for concurrent use. In session mode, the backing map comes from {@link
 * OrmSession#getCache(Class)} which returns a {@link ConcurrentHashMap}. In local mode, a new
 * {@code ConcurrentHashMap} is created.
 *
 * @param <T> the entity type stored in this map
 */
public final class IdentityMap<T> {

  private final Map<Object, T> localCache;
  private final OrmSession session;
  private final Class<T> entityClass;
  private final boolean enabled;

  /**
   * Creates a session-scoped identity map. Cache enablement is delegated to {@link
   * OrmSession#isUseCache()}.
   */
  IdentityMap(OrmSession session, Class<T> entityClass) {
    this(session, entityClass, true);
  }

  /**
   * Creates an identity map.
   *
   * <p><b>Session mode:</b> if {@code session} is non-null, {@code useCache} is ignored — the
   * session's own cache setting controls enablement. <b>Local mode:</b> if {@code session} is null,
   * {@code useCache} directly controls enablement and a private {@link ConcurrentHashMap} is used.
   *
   * @param session the owning session, or {@code null} for a private cache
   * @param entityClass the entity type
   * @param useCache whether caching is enabled (only respected when {@code session} is null)
   */
  IdentityMap(OrmSession session, Class<T> entityClass, boolean useCache) {
    this.session = session;
    this.entityClass = entityClass;
    if (session != null) {
      this.enabled = session.isUseCache();
      this.localCache = session.getCache(entityClass);
    } else {
      this.enabled = useCache;
      this.localCache = new ConcurrentHashMap<>();
    }
  }

  /**
   * Resolves the entity from cache. If not present, registers the given entity under its ID and
   * returns it. If an entity with the same ID already exists in the cache, returns the existing
   * instance (identity-map semantics).
   *
   * <p>When caching is disabled, returns the given entity directly without any cache interaction.
   *
   * @param id the entity's primary key value
   * @param entity the entity to cache-or-return
   * @return the cached instance if present, otherwise the given entity
   */
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

  /**
   * Looks up an entity by its primary key in the cache.
   *
   * @param id the primary key value
   * @return the cached entity, or {@code null} if not found or caching is disabled
   */
  @SuppressWarnings("unchecked")
  public T resolve(Object id) {
    if (!this.enabled) {
      return null;
    }
    if (this.session != null) {
      return this.session.getIdentity(this.entityClass, id);
    }
    return this.localCache.get(id);
  }

  /**
   * Stores an entity in the cache under its primary key. No-op if caching is disabled.
   *
   * @param id the primary key value
   * @param entity the entity to store
   */
  @SuppressWarnings("unchecked")
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

  /**
   * Removes the entity with the given primary key from the cache. No-op if caching is disabled.
   *
   * @param id the primary key value to evict
   */
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

  /** Removes all cached entities from the local cache. */
  public void clear() {
    this.localCache.clear();
  }
}
