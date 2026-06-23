package com.github.paperorm.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class SqliteDatabaseConnection extends DataSourceDatabaseConnection {

  public SqliteDatabaseConnection(Path databasePath) {
    this(databasePath, null);
  }

  public SqliteDatabaseConnection(Path databasePath, Logger logger) {
    super(createSqliteDataSource(databasePath, logger), logger);
  }

  private static HikariDataSource createSqliteDataSource(Path databasePath, Logger logger) {
    var activeLogger =
        logger != null ? logger : Logger.getLogger(SqliteDatabaseConnection.class.getName());
    var url = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    activeLogger.info(
        "Initializing SQLite Database connection pool at path: " + databasePath.toAbsolutePath());

    var config = new HikariConfig();
    config.setJdbcUrl(url);
    config.setMaximumPoolSize(10);
    config.setConnectionInitSql(
        "PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL; PRAGMA busy_timeout = 5000;");

    // Enable Prepared Statement caching for Hikari / SQLite
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

    return new HikariDataSource(config);
  }
}
