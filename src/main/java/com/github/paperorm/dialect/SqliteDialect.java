package com.github.paperorm.dialect;

public final class SqliteDialect extends AbstractSqlDialect {

  @Override
  protected String openingQuote() {
    return "\"";
  }

  @Override
  protected String closingQuote() {
    return "\"";
  }

  @Override
  protected String autoIncrementKeyword() {
    return "AUTOINCREMENT";
  }

  @Override
  public String currentTimestampDefault() {
    return "unixepoch()";
  }
}
