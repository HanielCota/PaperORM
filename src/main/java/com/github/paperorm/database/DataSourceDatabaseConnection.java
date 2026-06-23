package com.github.paperorm.database;

import com.github.paperorm.exception.ConnectionException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

public class DataSourceDatabaseConnection implements DatabaseConnection {

  private final DataSource dataSource;
  private final Logger logger;
  private final ThreadLocal<Connection> activeTransactionConnection = new ThreadLocal<>();

  public DataSourceDatabaseConnection(DataSource dataSource) {
    this(dataSource, null);
  }

  public DataSourceDatabaseConnection(DataSource dataSource, Logger logger) {
    this.dataSource = dataSource;
    this.logger =
        logger != null ? logger : Logger.getLogger(DataSourceDatabaseConnection.class.getName());
  }

  @Override
  public Connection openConnection() throws SQLException {
    var txConn = activeTransactionConnection.get();
    if (txConn != null) {
      return (Connection)
          Proxy.newProxyInstance(
              Connection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                if ("close".equals(method.getName())) {
                  return null;
                }
                try {
                  return method.invoke(txConn, args);
                } catch (InvocationTargetException e) {
                  throw e.getCause();
                }
              });
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
    var existingConnection = activeTransactionConnection.get();
    if (existingConnection != null) {
      try {
        return callback.apply(existingConnection);
      } catch (SQLException exception) {
        throw new ConnectionException("Transaction failed", exception);
      }
    }

    try (var connection = this.dataSource.getConnection()) {
      var originalAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      activeTransactionConnection.set(connection);

      try {
        var result = callback.apply(connection);
        connection.commit();
        return result;
      } catch (Exception exception) {
        logger.log(Level.SEVERE, "Transaction failed, rolling back changes in database", exception);
        try {
          connection.rollback();
        } catch (SQLException rollbackException) {
          exception.addSuppressed(rollbackException);
        }
        switch (exception) {
          case SQLException sqlException ->
              throw new ConnectionException("Transaction failed", sqlException);
          case RuntimeException runtimeException -> throw runtimeException;
          default ->
              throw new ConnectionException("Transaction failed with checked exception", exception);
        }
      } finally {
        activeTransactionConnection.remove();
        try {
          connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException ignored) {
        }
      }
    } catch (SQLException exception) {
      throw new ConnectionException("Failed to open connection for transaction", exception);
    }
  }

  @Override
  public void close() {
    if (this.dataSource instanceof AutoCloseable closeable) {
      try {
        logger.info("Closing Database connection pool.");
        closeable.close();
      } catch (Exception exception) {
        logger.log(Level.SEVERE, "Failed to close DataSource", exception);
      }
    }
  }
}
