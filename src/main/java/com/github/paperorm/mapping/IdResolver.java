package com.github.paperorm.mapping;

import com.github.paperorm.annotation.Id;
import com.github.paperorm.exception.MappingException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IdResolver {

  private static final Map<Class<?>, Field> CACHE = new ConcurrentHashMap<>();

  private IdResolver() {}

  public static Field resolve(Class<?> clazz) {
    var cached = CACHE.get(clazz);
    if (cached != null) {
      return cached;
    }
    for (var field : clazz.getDeclaredFields()) {
      if (field.isAnnotationPresent(Id.class)) {
        field.setAccessible(true);
        CACHE.put(clazz, field);
        return field;
      }
    }
    throw new MappingException("Entity " + clazz.getName() + " has no @Id field");
  }
}
