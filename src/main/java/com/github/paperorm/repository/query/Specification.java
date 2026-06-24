package com.github.paperorm.repository.query;

import com.github.paperorm.dialect.SqlDialect;
import java.util.ArrayList;
import java.util.List;

public interface Specification<T> {

  String toSql(SqlDialect dialect);

  List<Object> getParameters();

  record AndSpecification<T>(Specification<T> left, Specification<T> right)
      implements Specification<T> {
    @Override
    public String toSql(SqlDialect dialect) {
      return "(%s) AND (%s)".formatted(left.toSql(dialect), right.toSql(dialect));
    }

    @Override
    public List<Object> getParameters() {
      var combined = new ArrayList<>(left.getParameters());
      combined.addAll(right.getParameters());
      return combined;
    }
  }

  record OrSpecification<T>(Specification<T> left, Specification<T> right)
      implements Specification<T> {
    @Override
    public String toSql(SqlDialect dialect) {
      return "(%s) OR (%s)".formatted(left.toSql(dialect), right.toSql(dialect));
    }

    @Override
    public List<Object> getParameters() {
      var combined = new ArrayList<>(left.getParameters());
      combined.addAll(right.getParameters());
      return combined;
    }
  }

  record NotSpecification<T>(Specification<T> spec) implements Specification<T> {
    @Override
    public String toSql(SqlDialect dialect) {
      return "NOT (%s)".formatted(spec.toSql(dialect));
    }

    @Override
    public List<Object> getParameters() {
      return spec.getParameters();
    }
  }

  default Specification<T> and(Specification<T> other) {
    return new AndSpecification<>(this, other);
  }

  default Specification<T> or(Specification<T> other) {
    return new OrSpecification<>(this, other);
  }

  default Specification<T> not() {
    return new NotSpecification<>(this);
  }
}
