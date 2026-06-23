package com.github.paperorm.database;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface VoidTransactionCallback {

  void apply(Connection connection) throws SQLException;
}
