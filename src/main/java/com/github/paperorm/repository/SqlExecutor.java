package com.github.paperorm.repository;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.exception.OrmException;
import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
interface ConnectionAction<R> {
  R execute(Connection connection) throws SQLException;
}

final class SqlExecutor {

  private final DatabaseConnection connection;

  SqlExecutor(DatabaseConnection connection) {
    this.connection = connection;
  }

  <R> R execute(String errorMessage, ConnectionAction<R> action) {
    try (var conn = this.connection.openConnection()) {
      return action.execute(conn);
    } catch (SQLException exception) {
      throw new OrmException(errorMessage, exception);
    }
  }

  void execute(String errorMessage, VoidConnectionAction action) {
    try (var conn = this.connection.openConnection()) {
      action.execute(conn);
    } catch (SQLException exception) {
      throw new OrmException(errorMessage, exception);
    }
  }

  @FunctionalInterface
  interface VoidConnectionAction {
    void execute(Connection connection) throws SQLException;
  }
}
