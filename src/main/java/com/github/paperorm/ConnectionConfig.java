package com.github.paperorm;

import com.github.paperorm.database.DataSourceDatabaseConnection;
import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.MySqlDialect;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.dialect.SqliteDialect;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

public sealed interface ConnectionConfig {

  DatabaseConnection createConnection(Logger logger);

  SqlDialect defaultDialect();

  private static HikariConfig createBasePoolConfig() {
    var config = new HikariConfig();
    config.setMaximumPoolSize(10);
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    return config;
  }

  record Sqlite(Path path) implements ConnectionConfig {
    public Sqlite {
      Objects.requireNonNull(path, "path cannot be null");
    }

    @Override
    public DatabaseConnection createConnection(Logger logger) {
      var config = ConnectionConfig.createBasePoolConfig();
      config.setJdbcUrl("jdbc:sqlite:%s".formatted(path.toAbsolutePath()));
      config.setConnectionInitSql(
          "PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL; PRAGMA busy_timeout = 5000;");

      return new DataSourceDatabaseConnection(new HikariDataSource(config), logger);
    }

    @Override
    public SqlDialect defaultDialect() {
      return new SqliteDialect();
    }
  }

  record MySql(String host, int port, String database, String username, String password)
      implements ConnectionConfig {
    public MySql {
      Objects.requireNonNull(host, "host cannot be null");
      Objects.requireNonNull(database, "database cannot be null");
      Objects.requireNonNull(username, "username cannot be null");
      Objects.requireNonNull(password, "password cannot be null");
    }

    @Override
    public DatabaseConnection createConnection(Logger logger) {
      var config = ConnectionConfig.createBasePoolConfig();
      config.setJdbcUrl("jdbc:mysql://%s:%d/%s".formatted(host, port, database));
      config.setUsername(username);
      config.setPassword(password);
      config.addDataSourceProperty("useServerPrepStmts", "true");

      return new DataSourceDatabaseConnection(new HikariDataSource(config), logger);
    }

    @Override
    public SqlDialect defaultDialect() {
      return new MySqlDialect();
    }
  }

  record Custom(DatabaseConnection connection, SqlDialect dialect) implements ConnectionConfig {
    public Custom {
      Objects.requireNonNull(connection, "connection cannot be null");
      Objects.requireNonNull(dialect, "dialect cannot be null");
    }

    @Override
    public DatabaseConnection createConnection(Logger logger) {
      return connection;
    }

    @Override
    public SqlDialect defaultDialect() {
      return dialect;
    }
  }
}
