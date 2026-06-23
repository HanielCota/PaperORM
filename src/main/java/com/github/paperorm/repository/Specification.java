package com.github.paperorm.repository;

import com.github.paperorm.dialect.SqlDialect;
import java.util.ArrayList;
import java.util.List;

public interface Specification<T> {

  String toSql(SqlDialect dialect);

  List<Object> getParameters();

  default Specification<T> and(Specification<T> other) {
    return new Specification<>() {
      @Override
      public String toSql(SqlDialect dialect) {
        return "(" + Specification.this.toSql(dialect) + ") AND (" + other.toSql(dialect) + ")";
      }

      @Override
      public List<Object> getParameters() {
        var combined = new ArrayList<>(Specification.this.getParameters());
        combined.addAll(other.getParameters());
        return combined;
      }
    };
  }

  default Specification<T> or(Specification<T> other) {
    return new Specification<>() {
      @Override
      public String toSql(SqlDialect dialect) {
        return "(" + Specification.this.toSql(dialect) + ") OR (" + other.toSql(dialect) + ")";
      }

      @Override
      public List<Object> getParameters() {
        var combined = new ArrayList<>(Specification.this.getParameters());
        combined.addAll(other.getParameters());
        return combined;
      }
    };
  }

  default Specification<T> not() {
    return new Specification<>() {
      @Override
      public String toSql(SqlDialect dialect) {
        return "NOT (" + Specification.this.toSql(dialect) + ")";
      }

      @Override
      public List<Object> getParameters() {
        return Specification.this.getParameters();
      }
    };
  }
}
