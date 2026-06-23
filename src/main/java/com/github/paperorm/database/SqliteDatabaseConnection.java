package com.github.paperorm.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;

public final class SqliteDatabaseConnection extends DataSourceDatabaseConnection {

  private static final System.Logger LOGGER =
      System.getLogger(SqliteDatabaseConnection.class.getName());

  public SqliteDatabaseConnection(Path databasePath) {
    super(createSqliteDataSource(databasePath));
  }

  private static HikariDataSource createSqliteDataSource(Path databasePath) {
    var url = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    LOGGER.log(
        System.Logger.Level.INFO,
        "Initializing SQLite Database connection pool at path: {0}",
        databasePath.toAbsolutePath());

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
