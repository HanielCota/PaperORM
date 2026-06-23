package com.github.paperorm.mapping;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TypeMapperTest {

  private TypeMapper typeMapper;

  @BeforeEach
  void setUp() {
    typeMapper = new TypeMapper();
  }

  @Test
  void shouldRegisterCustomConverter() {
    var converter =
        new TypeConverter<String>() {
          @Override
          public Class<String> getType() {
            return String.class;
          }

          @Override
          public void setParameter(java.sql.PreparedStatement statement, int index, String value) {}

          @Override
          public String readValue(java.sql.ResultSet resultSet, String columnName) {
            return "custom";
          }
        };
    typeMapper.registerConverter(converter);
  }

  @Test
  void shouldInferTextSqlTypeForString() {
    assertEquals("TEXT", TypeMapper.sqlTypeFor(String.class));
  }

  @Test
  void shouldInferTextSqlTypeForUuid() {
    assertEquals("TEXT", TypeMapper.sqlTypeFor(UUID.class));
  }

  @Test
  void shouldInferTextSqlTypeForBigDecimal() {
    assertEquals("TEXT", TypeMapper.sqlTypeFor(BigDecimal.class));
  }

  @Test
  void shouldInferTextSqlTypeForLocalDateTime() {
    assertEquals("TEXT", TypeMapper.sqlTypeFor(LocalDateTime.class));
  }

  @Test
  void shouldInferTextSqlTypeForInstant() {
    assertEquals("TEXT", TypeMapper.sqlTypeFor(Instant.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForInt() {
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(int.class));
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(Integer.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForLong() {
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(long.class));
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(Long.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForShort() {
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(short.class));
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(Short.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForByte() {
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(byte.class));
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(Byte.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForBoolean() {
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(boolean.class));
    assertEquals("INTEGER", TypeMapper.sqlTypeFor(Boolean.class));
  }

  @Test
  void shouldInferRealSqlTypeForDouble() {
    assertEquals("REAL", TypeMapper.sqlTypeFor(double.class));
    assertEquals("REAL", TypeMapper.sqlTypeFor(Double.class));
  }

  @Test
  void shouldInferRealSqlTypeForFloat() {
    assertEquals("REAL", TypeMapper.sqlTypeFor(float.class));
    assertEquals("REAL", TypeMapper.sqlTypeFor(Float.class));
  }

  @Test
  void shouldInferBlobSqlTypeForByteArray() {
    assertEquals("BLOB", TypeMapper.sqlTypeFor(byte[].class));
  }

  @Test
  void shouldInferTextSqlTypeForEnum() {
    assertEquals("TEXT", TypeMapper.sqlTypeFor(SampleEnum.class));
  }

  @Test
  void shouldInferTextForNull() {
    assertEquals("TEXT", TypeMapper.sqlTypeFor(null));
  }

  @Test
  void shouldInferTextForUnknownTypes() {
    assertEquals("TEXT", TypeMapper.sqlTypeFor(TypeMapperTest.class));
  }

  enum SampleEnum {
    VALUE_A,
    VALUE_B
  }
}
