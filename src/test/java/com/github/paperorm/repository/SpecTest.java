package com.github.paperorm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.dialect.StandardSqlDialect;
import com.github.paperorm.repository.query.Spec;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpecTest {

  private SqlDialect dialect;

  @BeforeEach
  void setUp() {
    dialect = new StandardSqlDialect();
  }

  @Test
  void shouldBuildSimpleWhereClause() {
    var spec = Spec.where("name").eq("John");
    assertEquals("\"name\" = ?", spec.toSql(dialect));
    assertEquals(List.of("John"), spec.getParameters());
  }

  @Test
  void shouldBuildAndClause() {
    var spec = Spec.where("name").eq("John").and("age").greaterThan(18);
    assertEquals("\"name\" = ? AND \"age\" > ?", spec.toSql(dialect));
    assertEquals(List.of("John", 18), spec.getParameters());
  }

  @Test
  void shouldBuildOrClause() {
    var spec = Spec.where("status").eq("active").or("status").eq("pending");
    assertEquals("\"status\" = ? OR \"status\" = ?", spec.toSql(dialect));
    assertEquals(List.of("active", "pending"), spec.getParameters());
  }

  @Test
  void shouldBuildInClause() {
    var spec = Spec.where("id").in(1, 2, 3);
    assertEquals("\"id\" IN (?, ?, ?)", spec.toSql(dialect));
    assertEquals(List.of(1, 2, 3), spec.getParameters());
  }

  @Test
  void shouldBuildIsNullClause() {
    var spec = Spec.where("deleted_at").isNull();
    assertEquals("\"deleted_at\" IS NULL", spec.toSql(dialect));
    assertEquals(List.of(), spec.getParameters());
  }

  @Test
  void shouldBuildIsNotNullClause() {
    var spec = Spec.where("email").isNotNull();
    assertEquals("\"email\" IS NOT NULL", spec.toSql(dialect));
    assertEquals(List.of(), spec.getParameters());
  }

  @Test
  void shouldBuildOrderBy() {
    var spec = Spec.where("name").eq("John").orderBy("created_at", "DESC");
    assertEquals("\"name\" = ? ORDER BY \"created_at\" DESC", spec.toSql(dialect));
    assertEquals(List.of("John"), spec.getParameters());
  }

  @Test
  void shouldBuildLimitAndOffset() {
    var spec = Spec.where("active").eq(true).limit(10).offset(20);
    assertEquals("\"active\" = ? LIMIT 10 OFFSET 20", spec.toSql(dialect));
    assertEquals(List.of(true), spec.getParameters());
  }

  @Test
  void shouldBuildOrderByLimitOffsetWithoutWhere() {
    var spec = Spec.where("name").eq("x").orderBy("id", "ASC").limit(5);
    assertEquals("\"name\" = ? ORDER BY \"id\" ASC LIMIT 5", spec.toSql(dialect));
  }

  @Test
  void shouldBuildNotEq() {
    var spec = Spec.where("status").notEq("deleted");
    assertEquals("\"status\" <> ?", spec.toSql(dialect));
    assertEquals(List.of("deleted"), spec.getParameters());
  }

  @Test
  void shouldBuildLessThanAndGreaterOrEqual() {
    var spec = Spec.where("price").lessThan(100).and("rating").greaterOrEqual(4);
    assertEquals("\"price\" < ? AND \"rating\" >= ?", spec.toSql(dialect));
    assertEquals(List.of(100, 4), spec.getParameters());
  }

  @Test
  void shouldComposeWithAnd() {
    var spec1 = Spec.where("age").greaterThan(18);
    var spec2 = Spec.where("status").eq("active");
    var composed = spec1.and(spec2);
    assertEquals("(\"age\" > ?) AND (\"status\" = ?)", composed.toSql(dialect));
    assertEquals(List.of(18, "active"), composed.getParameters());
  }

  @Test
  void shouldComposeWithOr() {
    var spec1 = Spec.where("role").eq("admin");
    var spec2 = Spec.where("role").eq("moderator");
    var composed = spec1.or(spec2);
    assertEquals("(\"role\" = ?) OR (\"role\" = ?)", composed.toSql(dialect));
    assertEquals(List.of("admin", "moderator"), composed.getParameters());
  }

  @Test
  void shouldComposeWithNot() {
    var spec = Spec.where("status").eq("deleted").not();
    assertEquals("NOT (\"status\" = ?)", spec.toSql(dialect));
    assertEquals(List.of("deleted"), spec.getParameters());
  }

  @Test
  void shouldThrowWhenColumnHasNoOperator() {
    assertThrows(IllegalStateException.class, () -> Spec.where("name").and("age"));
  }

  @Test
  void shouldThrowWhenNoActiveColumn() {
    assertThrows(IllegalStateException.class, () -> Spec.where("name").eq("x").eq("y"));
  }
}
