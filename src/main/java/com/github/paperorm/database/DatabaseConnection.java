package com.github.paperorm.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseConnection extends AutoCloseable {

  Connection openConnection() throws SQLException;

  void execute(String sql) throws SQLException;

  <T> T runInTransaction(TransactionCallback<T> callback);

  /**
   * Returns true if the current thread is currently inside a transaction managed by this
   * connection.
   */
  default boolean isInTransaction() {
    return false;
  }

  @Override
  void close();
}
