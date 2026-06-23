package com.github.paperorm.mapping;

import com.github.paperorm.exception.MappingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

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
        var idField = IdResolver.resolve(value.getClass());
        return idField.get(value);
      }
      return value;
    } catch (IllegalAccessException exception) {
      throw new MappingException("Failed to read field " + column.field().getName(), exception);
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

  private static void coerceAndSet(Field field, Object target, Object value) {
    var type = field.getType();

    if (value == null) {
      setField(field, target, null);
      return;
    }

    if ((type == Integer.class || type == int.class) && value instanceof Number num) {
      setField(field, target, num.intValue());
      return;
    }
    if ((type == Long.class || type == long.class) && value instanceof Number num) {
      setField(field, target, num.longValue());
      return;
    }
    if ((type == Short.class || type == short.class) && value instanceof Number num) {
      setField(field, target, num.shortValue());
      return;
    }
    if ((type == Byte.class || type == byte.class) && value instanceof Number num) {
      setField(field, target, num.byteValue());
      return;
    }
    if (type == String.class && !(value instanceof String)) {
      setField(field, target, value.toString());
      return;
    }
    if (type == UUID.class && value instanceof String str) {
      setField(field, target, UUID.fromString(str));
      return;
    }
    if (!type.isInstance(value)) {
      throw new MappingException(
          "ID type mismatch: expected "
              + type.getName()
              + " but got "
              + value.getClass().getName()
              + " with value "
              + value);
    }
    setField(field, target, value);
  }

  private static void setField(Field field, Object target, Object value) {
    try {
      field.set(target, value);
    } catch (IllegalAccessException e) {
      throw new MappingException("Failed to set field " + field.getName(), e);
    }
  }

  private static Object createReferencedShell(Class<?> clazz, Object idValue) {
    try {
      var ctor = clazz.getDeclaredConstructor();
      ctor.setAccessible(true);
      var shell = ctor.newInstance();

      var idField = IdResolver.resolve(clazz);
      coerceAndSet(idField, shell, idValue);
      return shell;
    } catch (ReflectiveOperationException exception) {
      throw new MappingException(
          "Failed to create referenced shell for " + clazz.getName(), exception);
    }
  }

  public void setGeneratedId(ColumnMetadata idColumn, T entity, long generatedId) {
    coerceAndSet(idColumn.field(), entity, Long.valueOf(generatedId));
  }

  public void setGeneratedId(ColumnMetadata idColumn, T entity, Object generatedId) {
    coerceAndSet(idColumn.field(), entity, generatedId);
  }
}
