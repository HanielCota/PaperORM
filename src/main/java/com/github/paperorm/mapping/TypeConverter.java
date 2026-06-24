package com.github.paperorm.mapping;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface TypeConverter<T> {

  Class<T> getType();

  void setParameter(PreparedStatement statement, int index, T value) throws SQLException;

  T readValue(ResultSet resultSet, String columnName) throws SQLException;

  default String getSqlType() {
    return "TEXT";
  }
}
