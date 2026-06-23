package com.github.paperorm.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseConnection extends AutoCloseable {

  Connection openConnection() throws SQLException;

  void execute(String sql) throws SQLException;

  <T> T runInTransaction(TransactionCallback<T> callback);

  default void runInTransaction(VoidTransactionCallback callback) {
    runInTransaction(
        conn -> {
          callback.apply(conn);
          return null;
        });
  }

  @Override
  void close();
}
