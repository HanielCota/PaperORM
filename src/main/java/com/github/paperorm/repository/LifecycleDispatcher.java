package com.github.paperorm.repository;

import com.github.paperorm.annotation.PostLoad;
import com.github.paperorm.annotation.PreDelete;
import com.github.paperorm.annotation.PrePersist;
import com.github.paperorm.annotation.PreUpdate;
import com.github.paperorm.exception.OrmException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LifecycleDispatcher<T> {

  private static final Map<Class<?>, List<Method>> PRE_PERSIST_CACHE = new ConcurrentHashMap<>();
  private static final Map<Class<?>, List<Method>> PRE_UPDATE_CACHE = new ConcurrentHashMap<>();
  private static final Map<Class<?>, List<Method>> PRE_DELETE_CACHE = new ConcurrentHashMap<>();
  private static final Map<Class<?>, List<Method>> POST_LOAD_CACHE = new ConcurrentHashMap<>();

  private final List<Method> prePersistMethods;
  private final List<Method> preUpdateMethods;
  private final List<Method> preDeleteMethods;
  private final List<Method> postLoadMethods;

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

  private void fire(T entity, List<Method> methods) {
    if (entity == null || methods.isEmpty()) {
      return;
    }
    for (var method : methods) {
      try {
        method.invoke(entity);
      } catch (ReflectiveOperationException e) {
        throw new OrmException(
            "Failed to invoke lifecycle method "
                + method.getName()
                + " on "
                + entity.getClass().getSimpleName(),
            e);
      }
    }
  }

  private static List<Method> scanMethods(
      Class<?> entityClass,
      Class<? extends Annotation> annotationClass,
      Map<Class<?>, List<Method>> cache) {
    return cache.computeIfAbsent(
        entityClass,
        clazz -> {
          var methods = new ArrayList<Method>();
          var current = clazz;
          while (current != null && current != Object.class) {
            for (var method : current.getDeclaredMethods()) {
              if (method.isAnnotationPresent(annotationClass)) {
                method.setAccessible(true);
                methods.add(method);
              }
            }
            current = current.getSuperclass();
          }
          return List.copyOf(methods);
        });
  }
}
