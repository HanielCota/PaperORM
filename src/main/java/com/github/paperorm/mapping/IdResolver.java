package com.github.paperorm.mapping;

import com.github.paperorm.annotation.Id;
import com.github.paperorm.exception.MappingException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IdResolver {

  private final Map<Class<?>, Field> cache = new ConcurrentHashMap<>();

  public Field resolve(Class<?> clazz) {
    return cache.computeIfAbsent(clazz, this::findAndPrepareIdField);
  }

  private Field findAndPrepareIdField(Class<?> clazz) {
    for (var current = clazz;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (var field : current.getDeclaredFields()) {
        if (!field.isAnnotationPresent(Id.class)) {
          continue;
        }

        if (!field.trySetAccessible()) {
          throw new MappingException(
              "Could not make @Id field "
                  + clazz.getName()
                  + "."
                  + field.getName()
                  + " accessible");
        }

        return field;
      }
    }

    throw new MappingException("Entity " + clazz.getName() + " has no @Id field");
  }
}
