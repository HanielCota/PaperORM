package com.github.paperorm.repository;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.exception.OrmException;
import java.sql.Connection;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;

@FunctionalInterface
interface ConnectionAction<R> {
  R execute(Connection connection) throws SQLException;
}

@FunctionalInterface
interface VoidConnectionAction {
  void execute(Connection connection) throws SQLException;
}

@RequiredArgsConstructor
final class SqlExecutor {

  private final DatabaseConnection connection;

  private <R> R executeInternal(String errorMessage, ConnectionAction<R> action) {
    try (var conn = this.connection.openConnection()) {
      return action.execute(conn);
    } catch (SQLException exception) {
      throw new OrmException(errorMessage, exception);
    }
  }

  <R> R execute(String errorMessage, ConnectionAction<R> action) {
    return executeInternal(errorMessage, action);
  }

  void executeVoid(String errorMessage, VoidConnectionAction action) {
    executeInternal(
        errorMessage,
        conn -> {
          action.execute(conn);
          return null;
        });
  }
}
