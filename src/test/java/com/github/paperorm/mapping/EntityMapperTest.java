package com.github.paperorm.mapping;

import static org.junit.jupiter.api.Assertions.*;

import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.ManyToOne;
import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.dialect.SqliteDialect;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityMapperTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private SqlDialect dialect;
  private TypeMapper typeMapper;

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    dialect = new SqliteDialect();
    typeMapper = new TypeMapper();
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  @Test
  void shouldMapRowToEntity() throws Exception {
    var mapper = new EntityMapper<>(SimpleEntity.class, typeMapper);
    var scanner = new ReflectionEntityScanner();
    var metadata = scanner.scan(SimpleEntity.class);

    connection.execute(
        "CREATE TABLE IF NOT EXISTS \"simple_entity\" (\"id\" INTEGER PRIMARY KEY AUTOINCREMENT, \"name\" TEXT"
            + " NOT NULL, \"score\" INTEGER NOT NULL)");

    connection.execute(
        "INSERT INTO \"simple_entity\" (\"name\", \"score\") VALUES ('TestName', 42)");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement(dialect.selectAll(metadata));
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());

      var entity = mapper.mapRow(rs, metadata.columns());

      assertNotNull(entity);
      assertEquals(1L, entity.id);
      assertEquals("TestName", entity.name);
      assertEquals(42, entity.score);
    }
  }

  @Test
  void shouldReadFieldValue() {
    var mapper = new EntityMapper<>(SimpleEntity.class, typeMapper);
    var scanner = new ReflectionEntityScanner();
    var metadata = scanner.scan(SimpleEntity.class);

    var entity = new SimpleEntity();
    entity.id = 7L;
    entity.name = "ReadMe";

    var idColumn = metadata.idColumn();
    var nameColumn =
        metadata.columns().stream()
            .filter(c -> "name".equals(c.columnName()))
            .findFirst()
            .orElseThrow();

    assertEquals(7L, mapper.readField(idColumn, entity));
    assertEquals("ReadMe", mapper.readField(nameColumn, entity));
  }

  @Test
  void shouldWriteFieldValue() {
    var mapper = new EntityMapper<>(SimpleEntity.class, typeMapper);
    var scanner = new ReflectionEntityScanner();
    var metadata = scanner.scan(SimpleEntity.class);

    var entity = new SimpleEntity();
    var nameColumn =
        metadata.columns().stream()
            .filter(c -> "name".equals(c.columnName()))
            .findFirst()
            .orElseThrow();

    mapper.writeField(nameColumn, entity, "Written");
    assertEquals("Written", entity.name);
  }

  @Test
  void shouldSetGeneratedId() {
    var mapper = new EntityMapper<>(SimpleEntity.class, typeMapper);
    var scanner = new ReflectionEntityScanner();
    var metadata = scanner.scan(SimpleEntity.class);

    var entity = new SimpleEntity();
    mapper.setGeneratedId(metadata.idColumn(), entity, 123L);

    assertEquals(123L, entity.id);
  }

  @Test
  void shouldRejectClassWithoutNoArgConstructor() {
    assertThrows(
        com.github.paperorm.exception.MappingException.class,
        () -> new EntityMapper<>(BadEntity.class, typeMapper));
  }

  @Test
  void shouldReadManyToOneFieldAsReferencedId() {
    var mapper = new EntityMapper<>(ParentEntity.class, typeMapper);
    var scanner = new ReflectionEntityScanner();
    var metadata = scanner.scan(ParentEntity.class);

    var child = new ChildEntity();
    child.id = 99L;

    var parent = new ParentEntity();
    parent.child = child;

    var childColumn =
        metadata.columns().stream().filter(ColumnMetadata::manyToOne).findFirst().orElseThrow();

    assertEquals(99L, mapper.readField(childColumn, parent));
  }

  @Test
  void shouldWriteManyToOneShell() {
    var mapper = new EntityMapper<>(ParentEntity.class, typeMapper);
    var scanner = new ReflectionEntityScanner();
    var metadata = scanner.scan(ParentEntity.class);

    var parent = new ParentEntity();
    var childColumn =
        metadata.columns().stream().filter(ColumnMetadata::manyToOne).findFirst().orElseThrow();

    mapper.writeField(childColumn, parent, 42L);

    assertNotNull(parent.child);
    assertEquals(42L, parent.child.id);
  }

  @Test
  void shouldWriteManyToOneNull() {
    var mapper = new EntityMapper<>(ParentEntity.class, typeMapper);
    var scanner = new ReflectionEntityScanner();
    var metadata = scanner.scan(ParentEntity.class);

    var parent = new ParentEntity();
    parent.child = new ChildEntity();

    var childColumn =
        metadata.columns().stream().filter(ColumnMetadata::manyToOne).findFirst().orElseThrow();

    mapper.writeField(childColumn, parent, null);
    assertNull(parent.child);
  }

  @Entity
  public static class SimpleEntity {
    @Id(autoIncrement = true)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int score;
  }

  @Entity
  public static class ChildEntity {
    @Id
    @Column(nullable = false)
    private Long id;
  }

  @Entity
  public static class ParentEntity {
    @Id
    @Column(nullable = false)
    private Long id;

    @ManyToOne
    @Column(nullable = false)
    private ChildEntity child;
  }

  public static class BadEntity {
    private String name;

    public BadEntity(String name) {
      this.name = name;
    }
  }
}
