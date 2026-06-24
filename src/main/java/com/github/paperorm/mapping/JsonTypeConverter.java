package com.github.paperorm.mapping;

import com.google.gson.Gson;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class JsonTypeConverter<T> implements TypeConverter<T> {

  private final Class<T> type;
  private final Gson gson;

  @Override
  public Class<T> getType() {
    return this.type;
  }

  @Override
  public void setParameter(PreparedStatement statement, int index, T value) throws SQLException {
    if (value == null) {
      statement.setString(index, null);
      return;
    }

    statement.setString(index, this.gson.toJson(value));
  }

  @Override
  public T readValue(ResultSet resultSet, String columnName) throws SQLException {
    var raw = resultSet.getString(columnName);
    if (raw == null) {
      return null;
    }

    return this.gson.fromJson(raw, this.type);
  }
}
