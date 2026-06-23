package com.github.paperorm.mapping;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TypeMapper {

  private final Map<Class<?>, TypeConverter<?>> builtinConverters = new ConcurrentHashMap<>();
  private final Map<Class<?>, TypeConverter<?>> customConverters = new ConcurrentHashMap<>();

  public TypeMapper() {
    registerBuiltin(String.class, PreparedStatement::setString, ResultSet::getString);

    registerBuiltin(int.class, (stmt, i, v) -> stmt.setInt(i, v), (rs, col) -> rs.getInt(col));

    registerBuiltin(
        Integer.class,
        (stmt, i, v) -> stmt.setInt(i, v),
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).intValue();
        });

    registerBuiltin(long.class, (stmt, i, v) -> stmt.setLong(i, v), (rs, col) -> rs.getLong(col));

    registerBuiltin(
        Long.class,
        (stmt, i, v) -> stmt.setLong(i, v),
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).longValue();
        });

    registerBuiltin(
        double.class, (stmt, i, v) -> stmt.setDouble(i, v), (rs, col) -> rs.getDouble(col));

    registerBuiltin(
        Double.class,
        (stmt, i, v) -> stmt.setDouble(i, v),
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).doubleValue();
        });

    registerBuiltin(
        float.class, (stmt, i, v) -> stmt.setFloat(i, v), (rs, col) -> rs.getFloat(col));

    registerBuiltin(
        Float.class,
        (stmt, i, v) -> stmt.setFloat(i, v),
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).floatValue();
        });

    registerBuiltin(
        boolean.class, (stmt, i, v) -> stmt.setInt(i, v ? 1 : 0), (rs, col) -> rs.getInt(col) != 0);

    registerBuiltin(
        Boolean.class,
        (stmt, i, v) -> stmt.setInt(i, v ? 1 : 0),
        (rs, col) -> {
          var raw = rs.getObject(col);
          if (raw == null) {
            return null;
          }
          if (raw instanceof Boolean b) {
            return b;
          }
          return ((Number) raw).intValue() != 0;
        });

    registerBuiltin(
        short.class,
        (stmt, i, v) -> stmt.setInt(i, v.intValue()),
        (rs, col) -> (short) rs.getInt(col));

    registerBuiltin(
        Short.class,
        (stmt, i, v) -> stmt.setInt(i, v.intValue()),
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).shortValue();
        });

    registerBuiltin(
        byte.class,
        (stmt, i, v) -> stmt.setInt(i, v.intValue()),
        (rs, col) -> (byte) rs.getInt(col));

    registerBuiltin(
        Byte.class,
        (stmt, i, v) -> stmt.setInt(i, v.intValue()),
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).byteValue();
        });

    registerBuiltin(byte[].class, (stmt, i, v) -> stmt.setBytes(i, v), ResultSet::getBytes);

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

  public <T> void registerConverter(TypeConverter<T> converter) {
    customConverters.put(converter.getType(), converter);
  }

  @SuppressWarnings("unchecked")
  private TypeConverter<Object> findConverter(Class<?> clazz) {
    var custom = customConverters.get(clazz);
    if (custom != null) {
      return (TypeConverter<Object>) custom;
    }

    for (var entry : customConverters.entrySet()) {
      if (entry.getKey().isAssignableFrom(clazz)) {
        return (TypeConverter<Object>) entry.getValue();
      }
    }

    var builtin = builtinConverters.get(clazz);
    if (builtin != null) {
      return (TypeConverter<Object>) builtin;
    }

    return null;
  }

  public void setParameter(PreparedStatement statement, int index, Object value)
      throws SQLException {
    if (value == null) {
      statement.setObject(index, null);
      return;
    }

    var converter = findConverter(value.getClass());
    if (converter != null) {
      converter.setParameter(statement, index, value);
      return;
    }

    if (value instanceof Enum<?> enumValue) {
      statement.setString(index, enumValue.name());
      return;
    }

    statement.setObject(index, value);
  }

  public Object readColumnValue(ResultSet resultSet, ColumnMetadata column) throws SQLException {
    var type = column.field().getType();
    var columnName = column.columnName();

    var converter = findConverter(type);
    if (converter != null) {
      return converter.readValue(resultSet, columnName);
    }

    if (type.isEnum()) {
      var value = resultSet.getString(columnName);
      if (value == null) {
        return null;
      }
      @SuppressWarnings("unchecked")
      Class<? extends Enum> enumType = (Class<? extends Enum>) type;
      return Enum.valueOf(enumType, value);
    }

    return resultSet.getObject(columnName);
  }

  public static String sqlTypeFor(Class<?> type) {
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

  @FunctionalInterface
  private interface ParameterSetter<T> {
    void set(PreparedStatement statement, int index, T value) throws SQLException;
  }

  @FunctionalInterface
  private interface ColumnReader<T> {
    T read(ResultSet resultSet, String columnName) throws SQLException;
  }
}
