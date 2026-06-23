package com.github.paperorm.mapping;

public interface EntityScanner {

  EntityMetadata scan(Class<?> entityClass);
}
