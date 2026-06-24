package com.github.paperorm.database;

import com.github.paperorm.exception.ConnectionException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

public class DataSourceDatabaseConnection implements DatabaseConnection {

  private static final Logger DEFAULT_LOGGER =
      Logger.getLogger(DataSourceDatabaseConnection.class.getName());

  private final DataSource dataSource;
  private final Logger logger;
  private final ThreadLocal<Connection> activeTransactionConnection = new ThreadLocal<>();

  public DataSourceDatabaseConnection(DataSource dataSource) {
    this(dataSource, DEFAULT_LOGGER);
  }

  public DataSourceDatabaseConnection(DataSource dataSource, Logger logger) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    this.logger = Objects.requireNonNullElse(logger, DEFAULT_LOGGER);
  }

  @Override
  public Connection openConnection() throws SQLException {
    Connection txConn = activeTransactionConnection.get();
    if (txConn != null) {
      return txConn;
    }

    return this.dataSource.getConnection();
  }

  @Override
  public void execute(String sql) throws SQLException {
    try (var connection = openConnection();
        var statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  @Override
  public <T> T runInTransaction(TransactionCallback<T> callback) {
    Objects.requireNonNull(callback, "callback cannot be null");

    Connection existingConnection = activeTransactionConnection.get();
    if (existingConnection != null) {
      return executeCallback(callback, existingConnection);
    }

    try (Connection connection = this.dataSource.getConnection()) {
      return executeNewTransaction(callback, connection);
    } catch (SQLException e) {
      throw new ConnectionException("Failed to open connection for transaction", e);
    }
  }

  private <T> T executeCallback(TransactionCallback<T> callback, Connection connection) {
    try {
      return callback.apply(connection);
    } catch (SQLException e) {
      throw new ConnectionException("Transaction failed", e);
    }
  }

  private <T> T executeNewTransaction(TransactionCallback<T> callback, Connection connection)
      throws SQLException {
    boolean originalAutoCommit = connection.getAutoCommit();
    try {
      if (originalAutoCommit) {
        connection.setAutoCommit(false);
      }
      activeTransactionConnection.set(connection);

      T result = callback.apply(connection);
      connection.commit();
      return result;
    } catch (Exception e) {
      handleTransactionException(connection, e);
      throw e;
    } finally {
      activeTransactionConnection.remove();
      if (originalAutoCommit) {
        try {
          connection.setAutoCommit(true);
        } catch (SQLException ignored) {
          // auto-commit restore is best-effort; connection is being returned to pool
        }
      }
    }
  }

  private void handleTransactionException(Connection connection, Exception e) {
    logger.log(Level.SEVERE, "Transaction failed, rolling back changes", e);
    try {
      connection.rollback();
    } catch (SQLException rollbackEx) {
      e.addSuppressed(rollbackEx);
    }

    if (e instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new ConnectionException("Transaction failed", e);
  }

  @Override
  public void close() {
    if (!(this.dataSource instanceof AutoCloseable closeable)) {
      logger.warning("DataSource does not implement AutoCloseable; pool cannot be closed.");
      return;
    }

    try {
      logger.info("Closing database connection pool.");
      closeable.close();
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Failed to close DataSource", e);
    }
  }
}
