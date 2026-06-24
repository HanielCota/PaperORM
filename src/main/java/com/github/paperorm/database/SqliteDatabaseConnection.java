package com.github.paperorm.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqliteDatabaseConnection extends DataSourceDatabaseConnection {

  private static final String INIT_SQL =
      "PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL; PRAGMA busy_timeout = 5000;";

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
    config.setJdbcUrl("jdbc:sqlite:" + absolutePath);
    config.setMaximumPoolSize(10);
    config.setConnectionInitSql(INIT_SQL);

    // Cache config
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

    return new HikariDataSource(config);
  }
}
