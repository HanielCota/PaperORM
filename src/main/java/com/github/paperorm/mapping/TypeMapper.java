package com.github.paperorm.mapping;

import com.github.paperorm.exception.TypeConversionException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TypeMapper {

  private static final TypeConverter<Object> NULL_CONVERTER =
      new TypeConverter<>() {
        @Override
        public Class<Object> getType() {
          return Object.class;
        }

        @Override
        public void setParameter(PreparedStatement statement, int index, Object value)
            throws SQLException {
          if (value == null) {
            statement.setObject(index, null, java.sql.Types.NULL);
            return;
          }
          throw new TypeConversionException(
              "No type converter registered for " + value.getClass().getName());
        }

        @Override
        public Object readValue(ResultSet resultSet, String columnName) throws SQLException {
          return resultSet.getObject(columnName);
        }
      };

  private static final class ServiceLoaderCache {
    static final Map<Class<?>, TypeConverter<?>> LOADED;

    static {
      var map = new ConcurrentHashMap<Class<?>, TypeConverter<?>>();
      for (var converter : ServiceLoader.load(TypeConverter.class)) {
        map.put(converter.getType(), converter);
      }
      LOADED = map;
    }
  }

  private final Map<Class<?>, TypeConverter<?>> builtinConverters = new ConcurrentHashMap<>();
  private final Map<Class<?>, TypeConverter<?>> customConverters = new ConcurrentHashMap<>();
  private final Map<Class<?>, TypeConverter<?>> resolvedConverters = new ConcurrentHashMap<>();
  private final Map<Class<?>, String> customSqlTypes = new ConcurrentHashMap<>();

  @FunctionalInterface
  private interface ParameterSetter<T> {
    void set(PreparedStatement statement, int index, T value) throws SQLException;
  }

  @FunctionalInterface
  private interface ColumnReader<T> {
    T read(ResultSet resultSet, String columnName) throws SQLException;
  }

  public TypeMapper() {
    customConverters.putAll(ServiceLoaderCache.LOADED);
    registerPrimitives();
    registerWrappers();
    registerComplexTypes();
  }

  private void registerPrimitives() {
    registerBuiltin(String.class, PreparedStatement::setString, ResultSet::getString);
    registerBuiltin(int.class, PreparedStatement::setInt, ResultSet::getInt);
    registerBuiltin(long.class, PreparedStatement::setLong, ResultSet::getLong);
    registerBuiltin(double.class, PreparedStatement::setDouble, ResultSet::getDouble);
    registerBuiltin(float.class, PreparedStatement::setFloat, ResultSet::getFloat);
    registerBuiltin(boolean.class, (stmt, i, v) -> stmt.setBoolean(i, v), ResultSet::getBoolean);
    registerBuiltin(byte[].class, PreparedStatement::setBytes, ResultSet::getBytes);
  }

  private void registerWrappers() {
    registerBuiltin(
        short.class,
        (stmt, i, v) -> stmt.setInt(i, v.intValue()),
        (rs, col) -> (short) rs.getInt(col));
    registerBuiltin(
        byte.class,
        (stmt, i, v) -> stmt.setInt(i, v.intValue()),
        (rs, col) -> (byte) rs.getInt(col));

    registerBoxedNumeric(Integer.class, PreparedStatement::setInt, Number::intValue);
    registerBoxedNumeric(Long.class, PreparedStatement::setLong, Number::longValue);
    registerBoxedNumeric(Double.class, PreparedStatement::setDouble, Number::doubleValue);
    registerBoxedNumeric(Float.class, PreparedStatement::setFloat, Number::floatValue);
    registerBoxedNumeric(Short.class, (stmt, i, v) -> stmt.setInt(i, v), Number::shortValue);
    registerBoxedNumeric(Byte.class, (stmt, i, v) -> stmt.setInt(i, v), Number::byteValue);

    registerBuiltin(
        Boolean.class,
        (stmt, i, v) -> {
          if (v == null) {
            stmt.setNull(i, java.sql.Types.INTEGER);
          } else {
            stmt.setInt(i, v ? 1 : 0);
          }
        },
        (rs, col) -> {
          var raw = rs.getObject(col);
          if (raw == null) {
            return null;
          }
          if (raw instanceof Boolean b) {
            return b;
          }
          if (raw instanceof Number n) {
            return n.intValue() != 0;
          }
          throw new TypeConversionException(
              "Cannot convert " + raw.getClass().getName() + " to Boolean");
        });
  }

  private <T extends Number> void registerBoxedNumeric(
      Class<T> type, ParameterSetter<T> setter, java.util.function.Function<Number, T> extractor) {
    registerBuiltin(
        type,
        setter,
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : extractor.apply((Number) raw);
        });
  }

  private void registerComplexTypes() {
    registerBuiltin(
        UUID.class,
        (stmt, i, v) -> stmt.setString(i, v.toString()),
        (rs, col) -> {
          var raw = rs.getString(col);
          return raw == null ? null : UUID.fromString(raw);
        });
    registerBuiltin(
        BigDecimal.class,
        (stmt, i, v) -> stmt.setString(i, v.toPlainString()),
        (rs, col) -> {
          var raw = rs.getString(col);
          return raw == null ? null : new BigDecimal(raw);
        });
    registerBuiltin(
        LocalDateTime.class,
        (stmt, i, v) -> stmt.setString(i, v.toString()),
        (rs, col) -> {
          var raw = rs.getString(col);
          return raw == null ? null : LocalDateTime.parse(raw);
        });
    registerBuiltin(
        Instant.class,
        (stmt, i, v) -> stmt.setString(i, v.toString()),
        (rs, col) -> {
          var raw = rs.getString(col);
          return raw == null ? null : Instant.parse(raw);
        });
  }

  private <T> void registerBuiltin(
      Class<T> type, ParameterSetter<T> setter, ColumnReader<T> reader) {
    builtinConverters.put(
        type,
        new TypeConverter<T>() {
          @Override
          public Class<T> getType() {
            return type;
          }

          @Override
          public void setParameter(PreparedStatement statement, int index, T value)
              throws SQLException {
            setter.set(statement, index, value);
          }

          @Override
          public T readValue(ResultSet resultSet, String columnName) throws SQLException {
            return reader.read(resultSet, columnName);
          }
        });
  }

  public void registerConverter(TypeConverter<?> converter) {
    var type = converter.getType();
    customConverters.put(type, converter);
    customSqlTypes.put(type, converter.getSqlType());
    resolvedConverters.remove(type);
    resolvedConverters.keySet().removeIf(type::isAssignableFrom);
  }

  public void registerSqlType(Class<?> type, String sqlType) {
    customSqlTypes.put(type, sqlType);
  }

  private static final String INTEGER_SQL = "INTEGER";
  private static final Map<Class<?>, String> BUILTIN_SQL_TYPES =
      Map.ofEntries(
          Map.entry(int.class, INTEGER_SQL),
          Map.entry(Integer.class, INTEGER_SQL),
          Map.entry(long.class, INTEGER_SQL),
          Map.entry(Long.class, INTEGER_SQL),
          Map.entry(short.class, INTEGER_SQL),
          Map.entry(Short.class, INTEGER_SQL),
          Map.entry(byte.class, INTEGER_SQL),
          Map.entry(Byte.class, INTEGER_SQL),
          Map.entry(boolean.class, INTEGER_SQL),
          Map.entry(Boolean.class, INTEGER_SQL),
          Map.entry(double.class, "REAL"),
          Map.entry(Double.class, "REAL"),
          Map.entry(float.class, "REAL"),
          Map.entry(Float.class, "REAL"),
          Map.entry(byte[].class, "BLOB"));

  public String resolveSqlType(Class<?> type) {
    if (type == null) {
      return "TEXT";
    }

    if (type.isEnum()) {
      return "TEXT";
    }

    var builtin = BUILTIN_SQL_TYPES.get(type);
    if (builtin != null) {
      return builtin;
    }

    var custom = customSqlTypes.get(type);
    if (custom != null) {
      return custom;
    }

    for (var entry : customSqlTypes.entrySet()) {
      if (entry.getKey().isAssignableFrom(type)) {
        return entry.getValue();
      }
    }

    return "TEXT";
  }

  @SuppressWarnings("unchecked")
  private TypeConverter<Object> findConverter(Class<?> clazz) {
    var cached = resolvedConverters.get(clazz);
    if (cached != null) {
      return (TypeConverter<Object>) cached;
    }

    var custom = customConverters.get(clazz);
    if (custom != null) {
      resolvedConverters.put(clazz, custom);
      return (TypeConverter<Object>) custom;
    }

    for (var entry : customConverters.entrySet()) {
      if (entry.getKey().isAssignableFrom(clazz)) {
        resolvedConverters.put(clazz, entry.getValue());
        return (TypeConverter<Object>) entry.getValue();
      }
    }

    var builtin = builtinConverters.get(clazz);
    if (builtin != null) {
      resolvedConverters.put(clazz, builtin);
      return (TypeConverter<Object>) builtin;
    }

    for (var entry : builtinConverters.entrySet()) {
      if (entry.getKey().isAssignableFrom(clazz)) {
        resolvedConverters.put(clazz, entry.getValue());
        return (TypeConverter<Object>) entry.getValue();
      }
    }

    resolvedConverters.put(clazz, NULL_CONVERTER);
    return NULL_CONVERTER;
  }

  public void setParameter(PreparedStatement statement, int index, Object value)
      throws SQLException {
    if (value == null) {
      statement.setObject(index, null, java.sql.Types.NULL);
      return;
    }

    var converter = findConverter(value.getClass());
    if (converter != NULL_CONVERTER) {
      converter.setParameter(statement, index, value);
      return;
    }

    if (value instanceof Enum<?> enumValue) {
      statement.setString(index, enumValue.name());
      return;
    }

    throw new TypeConversionException(
        "No type converter registered for " + value.getClass().getName());
  }

  public Object readColumnValue(ResultSet resultSet, ColumnMetadata column) throws SQLException {
    var type = column.field().getType();
    var columnName = column.columnName();

    var converter = findConverter(type);
    if (converter != NULL_CONVERTER) {
      return converter.readValue(resultSet, columnName);
    }

    if (type.isEnum()) {
      var value = resultSet.getString(columnName);
      if (value == null) {
        return null;
      }

      return enumFromName(type, value);
    }

    return converter.readValue(resultSet, columnName);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Enum<?> enumFromName(Class<?> enumType, String name) {
    try {
      return Enum.valueOf((Class) enumType, name);
    } catch (IllegalArgumentException e) {
      throw new TypeConversionException(
          "Invalid enum constant '" + name + "' for type " + enumType.getName(), e);
    }
  }
}
