package com.github.paperorm.mapping;

import com.github.paperorm.annotation.Id;
import com.github.paperorm.exception.MappingException;
import java.lang.reflect.Constructor;
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
        return getEntityId(value);
      }
      return value;
    } catch (IllegalAccessException exception) {
      throw new MappingException("Failed to read field " + column.field().getName(), exception);
    }
  }

  private static Object getEntityId(Object entity) {
    try {
      for (var field : entity.getClass().getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
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

  private static Object createReferencedShell(Class<?> clazz, Object idValue) {
    try {
      var ctor = clazz.getDeclaredConstructor();
      ctor.setAccessible(true);
      var shell = ctor.newInstance();

      for (var field : clazz.getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
          field.setAccessible(true);
          var targetType = field.getType();

          if ((targetType == Integer.class || targetType == int.class)
              && idValue instanceof Number num) {
            field.set(shell, num.intValue());
            break;
          }
          if ((targetType == Long.class || targetType == long.class)
              && idValue instanceof Number num) {
            field.set(shell, num.longValue());
            break;
          }
          if (targetType == UUID.class && idValue instanceof String str) {
            field.set(shell, UUID.fromString(str));
            break;
          }
          if (idValue != null && !targetType.isInstance(idValue)) {
            throw new MappingException(
                "ID type mismatch for entity "
                    + clazz.getName()
                    + ": expected "
                    + targetType.getName()
                    + " but got "
                    + idValue.getClass().getName()
                    + " with value "
                    + idValue);
          }
          field.set(shell, idValue);
          break;
        }
      }
      return shell;
    } catch (ReflectiveOperationException exception) {
      throw new MappingException(
          "Failed to create referenced shell for " + clazz.getName(), exception);
    }
  }

  public void setGeneratedId(ColumnMetadata idColumn, T entity, long generatedId) {
    setGeneratedId(idColumn, entity, Long.valueOf(generatedId));
  }

  public void setGeneratedId(ColumnMetadata idColumn, T entity, Object generatedId) {
    var field = idColumn.field();
    var type = field.getType();

    if ((type == Integer.class || type == int.class) && generatedId instanceof Number num) {
      writeField(idColumn, entity, num.intValue());
      return;
    }
    if ((type == Long.class || type == long.class) && generatedId instanceof Number num) {
      writeField(idColumn, entity, num.longValue());
      return;
    }
    if ((type == Short.class || type == short.class) && generatedId instanceof Number num) {
      writeField(idColumn, entity, num.shortValue());
      return;
    }
    if ((type == Byte.class || type == byte.class) && generatedId instanceof Number num) {
      writeField(idColumn, entity, num.byteValue());
      return;
    }
    if (type == String.class) {
      writeField(idColumn, entity, String.valueOf(generatedId));
      return;
    }
    if (type == UUID.class && generatedId instanceof String str) {
      writeField(idColumn, entity, UUID.fromString(str));
      return;
    }
    writeField(idColumn, entity, generatedId);
  }
}
