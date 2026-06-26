package com.github.paperorm.database;

import com.github.paperorm.exception.ConnectionException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.DataSource;

public class DataSourceDatabaseConnection implements DatabaseConnection {

  private static final Logger DEFAULT_LOGGER =
      Logger.getLogger(DataSourceDatabaseConnection.class.getName());

  private final DataSource dataSource;
  private final Logger logger;
  private final ThreadLocal<TransactionHolder> activeTransaction = new ThreadLocal<>();

  public DataSourceDatabaseConnection(DataSource dataSource) {
    this(dataSource, DEFAULT_LOGGER);
  }

  public DataSourceDatabaseConnection(DataSource dataSource, Logger logger) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    this.logger = Objects.requireNonNullElse(logger, DEFAULT_LOGGER);
  }

  @Override
  public Connection openConnection() throws SQLException {
    var holder = activeTransaction.get();
    if (holder != null) {
      return createTransactionalProxy(holder.connection());
    }

    return this.dataSource.getConnection();
  }

  private Connection createTransactionalProxy(Connection txConn) {
    return (Connection)
        java.lang.reflect.Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              var name = method.getName();
              if ("close".equals(name) && (args == null || args.length == 0)) {
                return null;
              }
              if ("commit".equals(name) || "rollback".equals(name)) {
                throw new SQLException(
                    "Cannot call "
                        + name
                        + "() on a connection managed by an active PaperORM transaction."
                        + " Use the transaction callback instead.");
              }
              try {
                return method.invoke(txConn, args);
              } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getTargetException();
              }
            });
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

    var holder = activeTransaction.get();
    if (holder != null) {
      return executeNestedCallback(callback, holder);
    }

    try (Connection connection = this.dataSource.getConnection()) {
      return executeNewTransaction(callback, connection);
    } catch (SQLException e) {
      throw new ConnectionException("Failed to open connection for transaction", e);
    }
  }

  private <T> T executeNestedCallback(
      TransactionCallback<T> callback, TransactionHolder currentHolder) {
    currentHolder.depth().incrementAndGet();
    try {
      return callback.apply(currentHolder.connection());
    } catch (SQLException e) {
      throw new ConnectionException("Transaction failed", e);
    } finally {
      currentHolder.depth().decrementAndGet();
    }
  }

  private <T> T executeNewTransaction(TransactionCallback<T> callback, Connection connection)
      throws SQLException {
    boolean originalAutoCommit = connection.getAutoCommit();
    var holder = new TransactionHolder(connection, new AtomicInteger(1));
    try {
      if (originalAutoCommit) {
        connection.setAutoCommit(false);
      }
      activeTransaction.set(holder);

      T result = callback.apply(connection);
      connection.commit();
      return result;
    } catch (Exception e) {
      handleTransactionException(connection, e);
      throw e;
    } finally {
      if (holder.depth().get() <= 1) {
        activeTransaction.remove();
      }
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

  private record TransactionHolder(Connection connection, AtomicInteger depth) {}

  @Override
  public boolean isInTransaction() {
    var holder = activeTransaction.get();
    return holder != null && holder.depth().get() > 0;
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
