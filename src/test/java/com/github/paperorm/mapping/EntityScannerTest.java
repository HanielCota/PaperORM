package com.github.paperorm.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.paperorm.TestEntity;
import com.github.paperorm.annotation.Column;
import com.github.paperorm.annotation.Entity;
import com.github.paperorm.annotation.Id;
import com.github.paperorm.annotation.Table;
import com.github.paperorm.exception.MappingException;
import org.junit.jupiter.api.Test;

class EntityScannerTest {

  private final EntityScanner scanner = new ReflectionEntityScanner(new TypeMapper());

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

  @Test
  void camelToSnakeHandlesAbbreviations() {
    var metadata = this.scanner.scan(XmlParserEntity.class);
    assertEquals("xml_parser_entities", metadata.tableName());
    // Each column should be snake_case
    var columns = metadata.columns();
    assertEquals(4, columns.size());

    var parseXmlCol = columns.stream().filter(c -> c.columnName().equals("parse_xml")).findFirst();
    assertTrue(parseXmlCol.isPresent(), "Should have parse_xml column");

    var getUrlCol = columns.stream().filter(c -> c.columnName().equals("get_url")).findFirst();
    assertTrue(getUrlCol.isPresent(), "Should have get_url column");

    var normalCol = columns.stream().filter(c -> c.columnName().equals("normal_field")).findFirst();
    assertTrue(normalCol.isPresent(), "Should have normal_field column");
  }
}

@Entity
@Table(name = "xml_parser_entities")
class XmlParserEntity {
  @Id private Long id;

  @Column private String parseXML;

  @Column private String getURL;

  @Column private String normalField;
}
