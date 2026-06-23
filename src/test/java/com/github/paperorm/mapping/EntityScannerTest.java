package com.github.paperorm.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.paperorm.TestEntity;
import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.exception.MappingException;
import org.junit.jupiter.api.Test;

class EntityScannerTest {

  private final EntityScanner scanner = new ReflectionEntityScanner();

  @Test
  void scanEntityExtractsMetadata() {
    var metadata = this.scanner.scan(TestEntity.class);

    assertEquals("test_entities", metadata.tableName());
    assertEquals(4, metadata.columns().size());
    assertNotNull(metadata.idColumn());
    assertEquals("id", metadata.idColumn().columnName());
    assertTrue(metadata.idColumn().autoIncrement());
    assertFalse(metadata.idColumn().nullable());
  }

  @Test
  void scanClassWithoutEntityAnnotationThrows() {
    assertThrows(MappingException.class, () -> this.scanner.scan(String.class));
  }

  @Entity
  static class EntityWithoutId {
    @Column private String value;
  }

  @Test
  void scanEntityWithoutIdThrows() {
    assertThrows(MappingException.class, () -> this.scanner.scan(EntityWithoutId.class));
  }
}
