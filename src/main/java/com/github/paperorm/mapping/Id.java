package com.github.paperorm.mapping;

import java.util.Objects;

public record Id<T>(Object value) {

  public Id {
    Objects.requireNonNull(value, "id value cannot be null");
  }

  @SuppressWarnings("unchecked")
  public <V> V as(Class<V> expectedType) {
    if (!expectedType.isInstance(value)) {
      throw new ClassCastException(
          "Id value is "
              + value.getClass().getName()
              + " but "
              + expectedType.getName()
              + " was requested");
    }
    return (V) value;
  }

  public static <T> Id<T> of(Object value) {
    return new Id<>(value);
  }
}
