package com.github.paperorm.mapping;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public final class SqlTypeResolver {

  private SqlTypeResolver() {}

  public static String resolve(Class<?> type) {
    if (type == null) {
      return "TEXT";
    }
    if (type == String.class
        || type == UUID.class
        || type == BigDecimal.class
        || type == LocalDateTime.class
        || type == Instant.class
        || type.isEnum()) {
      return "TEXT";
    }
    if (type == int.class
        || type == Integer.class
        || type == long.class
        || type == Long.class
        || type == short.class
        || type == Short.class
        || type == byte.class
        || type == Byte.class
        || type == boolean.class
        || type == Boolean.class) {
      return "INTEGER";
    }
    if (type == double.class
        || type == Double.class
        || type == float.class
        || type == Float.class) {
      return "REAL";
    }
    if (type == byte[].class) {
      return "BLOB";
    }
    return "TEXT";
  }
}
