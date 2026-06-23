package com.github.paperorm.mapping;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.Table;
import com.github.paperorm.annotation.Transient;
import com.github.paperorm.exception.MappingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionEntityScanner implements EntityScanner {

  private final Map<Class<?>, EntityMetadata> cache = new ConcurrentHashMap<>();

  @Override
  public EntityMetadata scan(Class<?> entityClass) {
    return cache.computeIfAbsent(entityClass, this::scanDirectly);
  }

  private EntityMetadata scanDirectly(Class<?> entityClass) {
    if (!entityClass.isAnnotationPresent(Entity.class)) {
      throw new MappingException(
          "Class " + entityClass.getName() + " is not annotated with @Entity");
    }

    var tableName = camelToSnake(entityClass.getSimpleName());
    var tableAnnotation = entityClass.getAnnotation(Table.class);
    if (tableAnnotation != null && !tableAnnotation.name().isBlank()) {
      tableName = tableAnnotation.name();
    }

    var columns = new ArrayList<ColumnMetadata>();
    ColumnMetadata idColumn = null;

    for (var field : entityClass.getDeclaredFields()) {
      if (field.isSynthetic()
          || java.lang.reflect.Modifier.isStatic(field.getModifiers())
          || field.isAnnotationPresent(Transient.class)
          || (!field.isAnnotationPresent(Column.class)
              && !field.isAnnotationPresent(Id.class)
              && !field.isAnnotationPresent(com.github.paperorm.annotation.ManyToOne.class))) {
        continue;
      }

      if (!field.trySetAccessible()) {
        throw new MappingException(
            "Field "
                + entityClass.getName()
                + "."
                + field.getName()
                + " could not be made accessible");
      }

      var columnAnnotation = field.getAnnotation(Column.class);
      var idAnnotation = field.getAnnotation(Id.class);
      var manyToOneAnnotation = field.getAnnotation(com.github.paperorm.annotation.ManyToOne.class);

      var isManyToOne = manyToOneAnnotation != null;
      var referencedClass = isManyToOne ? field.getType() : null;

      if (isManyToOne && !referencedClass.isAnnotationPresent(Entity.class)) {
        throw new MappingException(
            "Referenced type "
                + referencedClass.getName()
                + " in field "
                + field.getName()
                + " must be annotated with @Entity");
      }

      var columnName = camelToSnake(field.getName());
      if (isManyToOne) {
        columnName = columnName + "_id";
        if (!manyToOneAnnotation.name().isBlank()) {
          columnName = manyToOneAnnotation.name();
        }
      }
      if (!isManyToOne && columnAnnotation != null && !columnAnnotation.name().isBlank()) {
        columnName = columnAnnotation.name();
      }

      var isId = idAnnotation != null;
      var autoIncrement = isId && idAnnotation.autoIncrement();
      var nullable = columnAnnotation == null || columnAnnotation.nullable();
      var unique = columnAnnotation != null && columnAnnotation.unique();
      var length = (columnAnnotation != null) ? columnAnnotation.length() : 255;
      var indexAnnotation = field.getAnnotation(com.github.paperorm.annotation.Index.class);
      var indexed = indexAnnotation != null;
      var indexName = indexed ? indexAnnotation.name() : "";

      var metadata =
          new ColumnMetadata(
              columnName,
              field,
              isId,
              autoIncrement,
              nullable,
              unique,
              length,
              isManyToOne,
              referencedClass,
              indexed,
              indexName);
      columns.add(metadata);

      if (isId) {
        if (idColumn != null) {
          throw new MappingException(
              "Entity " + entityClass.getName() + " has multiple @Id fields");
        }
        idColumn = metadata;
      }
    }

    if (idColumn == null) {
      throw new MappingException("Entity " + entityClass.getName() + " has no @Id field");
    }

    return new EntityMetadata(entityClass, tableName, List.copyOf(columns), idColumn);
  }

  private static String camelToSnake(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }

    var result = new StringBuilder(value.length() + 5);
    for (var i = 0; i < value.length(); i++) {
      var ch = value.charAt(i);

      if (!Character.isUpperCase(ch)) {
        result.append(ch);
        continue;
      }

      if (i > 0) {
        result.append('_');
      }
      result.append(Character.toLowerCase(ch));
    }

    return result.toString();
  }
}
