package com.github.paperorm.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqliteDatabaseConnection extends DataSourceDatabaseConnection {

  public SqliteDatabaseConnection(Path databasePath) {
    this(databasePath, null);
  }

  public SqliteDatabaseConnection(Path databasePath, Logger logger) {
    super(createDataSource(Objects.requireNonNull(databasePath, "databasePath"), logger), logger);
  }

  private static HikariDataSource createDataSource(Path path, Logger logger) {
    var log =
        Objects.requireNonNullElse(
            logger, Logger.getLogger(SqliteDatabaseConnection.class.getName()));
    var absolutePath = path.toAbsolutePath().toString();

    log.log(Level.INFO, "Initializing SQLite Database connection pool at path: {0}", absolutePath);

    var config = new HikariConfig();
    var normalizedPath = absolutePath.replace('\\', '/');

    config.setJdbcUrl(
        "jdbc:sqlite:file:"
            + normalizedPath
            + "?_journal_mode=WAL&_busy_timeout=5000&_foreign_keys=1");
    config.setMaximumPoolSize(10);

    // Cache config
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

    return new HikariDataSource(config);
  }
}
