package com.github.paperorm.dialect;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class MySqlDialect extends AbstractSqlDialect {

  private static final String MIGRATION_LOCK_NAME = "paper_orm_migrations";

  @Override
  protected String openingQuote() {
    return "`";
  }

  @Override
  protected String closingQuote() {
    return "`";
  }

  @Override
  protected String autoIncrementKeyword() {
    return "AUTO_INCREMENT";
  }

  @Override
  protected boolean includeUniqueInAddColumn() {
    return true;
  }

  @Override
  public String currentTimestampDefault() {
    return "UNIX_TIMESTAMP()";
  }

  @Override
  public boolean acquireMigrationLock(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT GET_LOCK('" + MIGRATION_LOCK_NAME + "', 10)")) {
      if (resultSet.next()) {
        return resultSet.getInt(1) == 1;
      }
      return false;
    }
  }

  @Override
  public void releaseMigrationLock(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeQuery("SELECT RELEASE_LOCK('" + MIGRATION_LOCK_NAME + "')");
    }
  }
}
