package com.github.paperorm.mapping;

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
          statement.setObject(index, value);
        }

        @Override
        public Object readValue(ResultSet resultSet, String columnName) throws SQLException {
          return resultSet.getObject(columnName);
        }
      };

  private final Map<Class<?>, TypeConverter<?>> builtinConverters = new ConcurrentHashMap<>();
  private final Map<Class<?>, TypeConverter<?>> customConverters = new ConcurrentHashMap<>();

  @FunctionalInterface
  private interface ParameterSetter<T> {
    void set(PreparedStatement statement, int index, T value) throws SQLException;
  }

  @FunctionalInterface
  private interface ColumnReader<T> {
    T read(ResultSet resultSet, String columnName) throws SQLException;
  }

  public TypeMapper() {
    for (var converter : ServiceLoader.load(TypeConverter.class)) {
      customConverters.put(converter.getType(), converter);
    }

    registerBuiltin(String.class, PreparedStatement::setString, ResultSet::getString);

    registerBuiltin(int.class, PreparedStatement::setInt, ResultSet::getInt);

    registerBuiltin(
        Integer.class,
        PreparedStatement::setInt,
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).intValue();
        });

    registerBuiltin(long.class, PreparedStatement::setLong, ResultSet::getLong);

    registerBuiltin(
        Long.class,
        PreparedStatement::setLong,
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).longValue();
        });

    registerBuiltin(double.class, PreparedStatement::setDouble, ResultSet::getDouble);

    registerBuiltin(
        Double.class,
        PreparedStatement::setDouble,
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).doubleValue();
        });

    registerBuiltin(float.class, PreparedStatement::setFloat, ResultSet::getFloat);

    registerBuiltin(
        Float.class,
        PreparedStatement::setFloat,
        (rs, col) -> {
          var raw = rs.getObject(col);
          return raw == null ? null : ((Number) raw).floatValue();
        });

    registerBuiltin(
        boolean.class, (stmt, i, v) -> stmt.setInt(i, v ? 1 : 0), (rs, col) -> rs.getInt(col) != 0);

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

    registerBuiltin(byte[].class, PreparedStatement::setBytes, ResultSet::getBytes);

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

    for (var entry : builtinConverters.entrySet()) {
      if (entry.getKey().isAssignableFrom(clazz)) {
        return (TypeConverter<Object>) entry.getValue();
      }
    }

    return NULL_CONVERTER;
  }

  public void setParameter(PreparedStatement statement, int index, Object value)
      throws SQLException {
    if (value == null) {
      statement.setObject(index, null);
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

    converter.setParameter(statement, index, value);
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

      @SuppressWarnings({"unchecked", "rawtypes"})
      var enumValue = Enum.valueOf((Class<Enum>) type, value);
      return enumValue;
    }

    return converter.readValue(resultSet, columnName);
  }

  /**
   * @deprecated Use {@link SqlTypeResolver#resolve(Class)} instead.
   */
  @Deprecated
  public static String sqlTypeFor(Class<?> type) {
    return SqlTypeResolver.resolve(type);
  }
}
