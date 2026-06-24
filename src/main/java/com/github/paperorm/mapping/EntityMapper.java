package com.github.paperorm.mapping;

import com.github.paperorm.exception.MappingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class EntityMapper<T> {

  private final Class<T> entityClass;
  private final TypeMapper typeMapper;
  private final IdResolver idResolver;
  private final Constructor<T> constructor;

  public EntityMapper(Class<T> entityClass, TypeMapper typeMapper, IdResolver idResolver) {
    this.entityClass = entityClass;
    this.typeMapper = typeMapper;
    this.idResolver = idResolver;
    this.constructor = resolveConstructor(entityClass);
  }

  private Constructor<T> resolveConstructor(Class<T> clazz) {
    return (Constructor<T>) getNoArgConstructor(clazz);
  }

  private static Constructor<?> getNoArgConstructor(Class<?> clazz) {
    try {
      var ctor = clazz.getDeclaredConstructor();
      if (!ctor.trySetAccessible()) {
        throw new MappingException(
            "Could not make constructor of " + clazz.getName() + " accessible");
      }
      return ctor;
    } catch (NoSuchMethodException e) {
      throw new MappingException("Entity " + clazz.getName() + " needs a no-args constructor", e);
    }
  }

  public T mapRow(ResultSet resultSet, List<ColumnMetadata> columns) throws SQLException {
    try {
      T entity = this.constructor.newInstance();
      for (var column : columns) {
        var value = this.typeMapper.readColumnValue(resultSet, column);
        writeField(column, entity, value);
      }
      return entity;
    } catch (ReflectiveOperationException e) {
      throw new MappingException("Failed to instantiate entity " + this.entityClass.getName(), e);
    }
  }

  public Object readField(ColumnMetadata column, T entity) {
    try {
      var field = column.field();
      var value = field.get(entity);
      if (!column.manyToOne() || value == null) {
        return value;
      }

      var idField = this.idResolver.resolve(value.getClass());
      return idField.get(value);
    } catch (IllegalAccessException e) {
      throw new MappingException("Failed to read field " + column.field().getName(), e);
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
    } catch (IllegalAccessException e) {
      throw new MappingException("Failed to write field " + column.field().getName(), e);
    }
  }

  private void coerceAndSet(Field field, Object target, Object value) {
    if (value == null) {
      if (!field.getType().isPrimitive()) {
        setField(field, target, null);
      }
      return;
    }

    var type = field.getType();
    if (value instanceof Number num) {
      var coerced = coerceNumber(num, type);
      if (coerced != null) {
        setField(field, target, coerced);
        return;
      }
    }

    if (type == String.class) {
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

  private static Object coerceNumber(Number num, Class<?> targetType) {
    if (targetType == Integer.class || targetType == int.class) {
      return num.intValue();
    }
    if (targetType == Long.class || targetType == long.class) {
      return num.longValue();
    }
    if (targetType == Short.class || targetType == short.class) {
      return num.shortValue();
    }
    if (targetType == Byte.class || targetType == byte.class) {
      return num.byteValue();
    }
    if (targetType == Float.class || targetType == float.class) {
      return num.floatValue();
    }
    if (targetType == Double.class || targetType == double.class) {
      return num.doubleValue();
    }
    return null;
  }

  private void setField(Field field, Object target, Object value) {
    try {
      field.set(target, value);
    } catch (IllegalAccessException e) {
      throw new MappingException("Failed to set field " + field.getName(), e);
    }
  }

  private Object createReferencedShell(Class<?> clazz, Object idValue) {
    try {
      var ctor = getNoArgConstructor(clazz);
      var shell = ctor.newInstance();
      var idField = this.idResolver.resolve(clazz);
      coerceAndSet(idField, shell, idValue);
      return shell;
    } catch (ReflectiveOperationException e) {
      throw new MappingException("Failed to create referenced shell for " + clazz.getName(), e);
    }
  }

  public void setGeneratedId(ColumnMetadata idColumn, T entity, Object generatedId) {
    coerceAndSet(idColumn.field(), entity, generatedId);
  }
}
