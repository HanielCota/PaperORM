package com.github.paperorm.dialect;

import com.github.paperorm.mapping.ColumnMetadata;

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
  protected String identityColumnSuffix(ColumnMetadata column) {
    return "PRIMARY KEY AUTOINCREMENT";
  }

  @Override
  public String currentTimestampDefault() {
    return "unixepoch()";
  }
}
