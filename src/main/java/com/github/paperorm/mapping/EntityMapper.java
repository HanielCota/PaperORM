package com.github.paperorm.mapping;

import com.github.paperorm.exception.MappingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class EntityMapper<T> {

  private final Class<T> entityClass;
  private final TypeMapper typeMapper;
  private final Constructor<T> constructor;

  public EntityMapper(Class<T> entityClass, TypeMapper typeMapper) {
    this.entityClass = entityClass;
    this.typeMapper = typeMapper;
    try {
      this.constructor = entityClass.getDeclaredConstructor();
      this.constructor.setAccessible(true);
    } catch (NoSuchMethodException exception) {
      throw new MappingException(
          "Entity " + entityClass.getName() + " needs a no-args constructor", exception);
    }
  }

  public T mapRow(ResultSet resultSet, List<ColumnMetadata> columns) throws SQLException {
    T entity;

    try {
      entity = this.constructor.newInstance();
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException exception) {
      throw new MappingException(
          "Failed to instantiate entity " + this.entityClass.getName(), exception);
    }

    for (var column : columns) {
      var value = this.typeMapper.readColumnValue(resultSet, column);
      writeField(column, entity, value);
    }

    return entity;
  }

  public Object readField(ColumnMetadata column, T entity) {
    try {
      var field = column.field();
      var value = field.get(entity);
      if (column.manyToOne() && value != null) {
        return getEntityId(value);
      }
      return value;
    } catch (IllegalAccessException exception) {
      throw new MappingException("Failed to read field " + column.field().getName(), exception);
    }
  }

  private Object getEntityId(Object entity) {
    try {
      for (var field : entity.getClass().getDeclaredFields()) {
        if (field.isAnnotationPresent(com.github.paperorm.annotation.Id.class)) {
          field.setAccessible(true);
          return field.get(entity);
        }
      }
      throw new MappingException(
          "Referenced entity " + entity.getClass().getName() + " has no @Id field");
    } catch (IllegalAccessException exception) {
      throw new MappingException("Failed to read referenced entity ID", exception);
    }
  }

  public void writeField(ColumnMetadata column, T entity, Object value) {
    try {
      var field = column.field();
      if (!column.manyToOne()) {
        field.set(entity, value);
        return;
      }

      if (value == null) {
        field.set(entity, null);
        return;
      }

      var shell = createReferencedShell(column.referencedClass(), value);
      field.set(entity, shell);
    } catch (IllegalAccessException exception) {
      throw new MappingException("Failed to write field " + column.field().getName(), exception);
    }
  }

  private Object createReferencedShell(Class<?> clazz, Object idValue) {
    try {
      var ctor = clazz.getDeclaredConstructor();
      ctor.setAccessible(true);
      var shell = ctor.newInstance();

      for (var field : clazz.getDeclaredFields()) {
        if (field.isAnnotationPresent(com.github.paperorm.annotation.Id.class)) {
          field.setAccessible(true);
          var targetType = field.getType();

          if ((targetType == Integer.class || targetType == int.class)
              && idValue instanceof Number num) {
            field.set(shell, num.intValue());
          } else if ((targetType == Long.class || targetType == long.class)
              && idValue instanceof Number num) {
            field.set(shell, num.longValue());
          } else if (targetType == java.util.UUID.class && idValue instanceof String str) {
            field.set(shell, java.util.UUID.fromString(str));
          } else {
            field.set(shell, idValue);
          }
          break;
        }
      }
      return shell;
    } catch (Exception exception) {
      throw new MappingException(
          "Failed to create referenced shell for " + clazz.getName(), exception);
    }
  }

  public void setGeneratedId(ColumnMetadata idColumn, T entity, long generatedId) {
    var type = idColumn.field().getType();

    if (type == Integer.class || type == int.class) {
      writeField(idColumn, entity, (int) generatedId);
      return;
    }

    writeField(idColumn, entity, generatedId);
  }
}
