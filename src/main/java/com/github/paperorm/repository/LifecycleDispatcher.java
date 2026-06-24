package com.github.paperorm.repository;

import com.github.paperorm.annotation.PostLoad;
import com.github.paperorm.annotation.PreDelete;
import com.github.paperorm.annotation.PrePersist;
import com.github.paperorm.annotation.PreUpdate;
import com.github.paperorm.exception.OrmException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LifecycleDispatcher<T> {

  private static final Map<Class<?>, List<MethodHandle>> PRE_PERSIST_CACHE =
      new ConcurrentHashMap<>();
  private static final Map<Class<?>, List<MethodHandle>> PRE_UPDATE_CACHE =
      new ConcurrentHashMap<>();
  private static final Map<Class<?>, List<MethodHandle>> PRE_DELETE_CACHE =
      new ConcurrentHashMap<>();
  private static final Map<Class<?>, List<MethodHandle>> POST_LOAD_CACHE =
      new ConcurrentHashMap<>();

  private final List<MethodHandle> prePersistMethods;
  private final List<MethodHandle> preUpdateMethods;
  private final List<MethodHandle> preDeleteMethods;
  private final List<MethodHandle> postLoadMethods;

  public LifecycleDispatcher(Class<T> entityClass) {
    this.prePersistMethods = scanMethods(entityClass, PrePersist.class, PRE_PERSIST_CACHE);
    this.preUpdateMethods = scanMethods(entityClass, PreUpdate.class, PRE_UPDATE_CACHE);
    this.preDeleteMethods = scanMethods(entityClass, PreDelete.class, PRE_DELETE_CACHE);
    this.postLoadMethods = scanMethods(entityClass, PostLoad.class, POST_LOAD_CACHE);
  }

  public void firePrePersist(T entity) {
    fire(entity, this.prePersistMethods);
  }

  public void firePreUpdate(T entity) {
    fire(entity, this.preUpdateMethods);
  }

  public void firePreDelete(T entity) {
    fire(entity, this.preDeleteMethods);
  }

  public void firePostLoad(T entity) {
    fire(entity, this.postLoadMethods);
  }

  private void fire(T entity, List<MethodHandle> methods) {
    if (entity == null || methods.isEmpty()) {
      return;
    }

    for (var method : methods) {
      try {
        method.invoke(entity);
      } catch (Throwable e) {
        throw new OrmException(
            "Failed to invoke lifecycle method on " + entity.getClass().getSimpleName(), e);
      }
    }
  }

  private static List<MethodHandle> scanMethods(
      Class<?> entityClass,
      Class<? extends Annotation> annotation,
      Map<Class<?>, List<MethodHandle>> cache) {
    return cache.computeIfAbsent(
        entityClass,
        clazz -> {
          var methods = new ArrayList<MethodHandle>();
          var lookup = MethodHandles.lookup();

          for (var current = clazz;
              current != null && current != Object.class;
              current = current.getSuperclass()) {
            for (var method : current.getDeclaredMethods()) {
              if (!method.isAnnotationPresent(annotation)) {
                continue;
              }

              if (!method.trySetAccessible()) {
                throw new OrmException(
                    "Could not make lifecycle method "
                        + clazz.getName()
                        + "."
                        + method.getName()
                        + " accessible");
              }

              try {
                methods.add(lookup.unreflect(method));
              } catch (IllegalAccessException e) {
                throw new OrmException("Failed to unreflect method " + method.getName(), e);
              }
            }
          }

          return List.copyOf(methods);
        });
  }
}
