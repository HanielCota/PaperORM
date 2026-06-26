package com.github.paperorm.dialect;

import java.sql.Connection;
import java.sql.SQLException;

public interface SqlDialect extends SchemaDialect, QueryDialect {

  /**
   * Acquires a database-level lock to prevent concurrent migration execution across processes.
   *
   * @param connection the connection to use
   * @return true if the lock was acquired
   * @throws SQLException if a database error occurs
   */
  default boolean acquireMigrationLock(Connection connection) throws SQLException {
    return true;
  }

  /**
   * Releases the database-level lock acquired by {@link #acquireMigrationLock(Connection)}.
   *
   * @param connection the connection to use
   * @throws SQLException if a database error occurs
   */
  default void releaseMigrationLock(Connection connection) throws SQLException {
    // no-op by default
  }
}
