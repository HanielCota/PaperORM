package com.github.paperorm.dialect;

import com.github.paperorm.mapping.ColumnMetadata;

public final class MySqlDialect extends AbstractSqlDialect {

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
  protected String identityColumnSuffix(ColumnMetadata column) {
    return "PRIMARY KEY AUTO_INCREMENT";
  }

  @Override
  protected boolean includeUniqueInAddColumn() {
    return true;
  }

  @Override
  public String currentTimestampDefault() {
    return "UNIX_TIMESTAMP()";
  }
}
