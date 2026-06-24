package com.github.paperorm.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseConnection extends AutoCloseable {

  Connection openConnection() throws SQLException;

  void execute(String sql) throws SQLException;

  <T> T runInTransaction(TransactionCallback<T> callback);

  @Override
  void close();
}
