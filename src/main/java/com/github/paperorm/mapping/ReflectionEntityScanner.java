package com.github.paperorm.mapping;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.Index;
import com.github.paperorm.annotation.ManyToOne;
import com.github.paperorm.annotation.Table;
import com.github.paperorm.annotation.Transient;
import com.github.paperorm.exception.MappingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionEntityScanner implements EntityScanner {

  private final Map<Class<?>, EntityMetadata> cache = new ConcurrentHashMap<>();
  private final IdResolver idResolver = new IdResolver();
  private final TypeMapper typeMapper;

  public ReflectionEntityScanner(TypeMapper typeMapper) {
    this.typeMapper = typeMapper;
  }

  @Override
  public EntityMetadata scan(Class<?> entityClass) {
    Objects.requireNonNull(entityClass, "entityClass cannot be null");
    return cache.computeIfAbsent(entityClass, this::scanDirectly);
  }

  private EntityMetadata scanDirectly(Class<?> entityClass) {
    if (!entityClass.isAnnotationPresent(Entity.class)) {
      throw new MappingException(
          "Class " + entityClass.getName() + " is not annotated with @Entity");
    }

    String tableName = resolveTableName(entityClass);
    List<ColumnMetadata> columns = new ArrayList<>();
    ColumnMetadata idColumn = null;
    var seenFields = new HashSet<String>();

    for (var current = entityClass;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (var field : current.getDeclaredFields()) {
        if (!seenFields.add(field.getName()) || shouldSkipField(field)) {
          continue;
        }

        ensureAccessible(current, field);
        ColumnMetadata metadata = createColumnMetadata(field);
        columns.add(metadata);

        if (metadata.id()) {
          idColumn = validateAndSetIdColumn(entityClass, idColumn, metadata);
        }
      }
    }

    if (idColumn == null) {
      throw new MappingException("Entity " + entityClass.getName() + " has no @Id field");
    }

    return new EntityMetadata(entityClass, tableName, List.copyOf(columns), idColumn);
  }

  private String resolveTableName(Class<?> entityClass) {
    var tableAnnotation = entityClass.getAnnotation(Table.class);
    if (tableAnnotation != null && !tableAnnotation.name().isBlank()) {
      return tableAnnotation.name();
    }
    return camelToSnake(entityClass.getSimpleName());
  }

  private boolean shouldSkipField(Field field) {
    return field.isSynthetic()
        || Modifier.isStatic(field.getModifiers())
        || field.isAnnotationPresent(Transient.class)
        || (!field.isAnnotationPresent(Column.class)
            && !field.isAnnotationPresent(Id.class)
            && !field.isAnnotationPresent(ManyToOne.class));
  }

  private void ensureAccessible(Class<?> entityClass, Field field) {
    if (!field.trySetAccessible()) {
      throw new MappingException(
          "Field "
              + entityClass.getName()
              + "."
              + field.getName()
              + " could not be made accessible");
    }
  }

  private ColumnMetadata createColumnMetadata(Field field) {
    var columnAnnotation = field.getAnnotation(Column.class);
    var idAnnotation = field.getAnnotation(Id.class);
    var manyToOneAnnotation = field.getAnnotation(ManyToOne.class);

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

    var columnName = resolveColumnName(field, columnAnnotation, manyToOneAnnotation);
    var isId = idAnnotation != null;
    var autoIncrement = isId && idAnnotation.autoIncrement();
    var nullable = columnAnnotation == null || columnAnnotation.nullable();
    var unique = columnAnnotation != null && columnAnnotation.unique();
    var length = (columnAnnotation != null) ? columnAnnotation.length() : 255;

    var indexAnnotation = field.getAnnotation(Index.class);
    var indexed = indexAnnotation != null;
    var indexName = indexed ? indexAnnotation.name() : "";

    var sqlType =
        this.typeMapper.resolveSqlType(
            isManyToOne ? this.idResolver.resolve(referencedClass).getType() : field.getType());

    return new ColumnMetadata(
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
        indexName,
        sqlType);
  }

  private String resolveColumnName(
      Field field, Column columnAnnotation, ManyToOne manyToOneAnnotation) {
    var baseName = camelToSnake(field.getName());

    if (manyToOneAnnotation != null) {
      if (!manyToOneAnnotation.name().isBlank()) {
        return manyToOneAnnotation.name();
      }
      return baseName + "_id";
    }

    if (columnAnnotation != null && !columnAnnotation.name().isBlank()) {
      return columnAnnotation.name();
    }

    return baseName;
  }

  private ColumnMetadata validateAndSetIdColumn(
      Class<?> entityClass, ColumnMetadata currentId, ColumnMetadata newId) {
    if (currentId != null) {
      throw new MappingException("Entity " + entityClass.getName() + " has multiple @Id fields");
    }
    return newId;
  }

  private static String camelToSnake(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    var result = new StringBuilder(value.length() + 5);
    for (var i = 0; i < value.length(); i++) {
      var ch = value.charAt(i);

      if (!Character.isUpperCase(ch)) {
        result.append(ch);
        continue;
      }

      if (i > 0) {
        var prev = value.charAt(i - 1);
        var next = i + 1 < value.length() ? value.charAt(i + 1) : '\0';
        if (Character.isLowerCase(prev) || (Character.isLowerCase(next))) {
          result.append('_');
        }
      }
      result.append(Character.toLowerCase(ch));
    }

    return result.toString();
  }
}
