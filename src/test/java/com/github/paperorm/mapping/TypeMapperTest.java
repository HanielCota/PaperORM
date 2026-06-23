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
    assertEquals("TEXT", SqlTypeResolver.resolve(String.class));
  }

  @Test
  void shouldInferTextSqlTypeForUuid() {
    assertEquals("TEXT", SqlTypeResolver.resolve(UUID.class));
  }

  @Test
  void shouldInferTextSqlTypeForBigDecimal() {
    assertEquals("TEXT", SqlTypeResolver.resolve(BigDecimal.class));
  }

  @Test
  void shouldInferTextSqlTypeForLocalDateTime() {
    assertEquals("TEXT", SqlTypeResolver.resolve(LocalDateTime.class));
  }

  @Test
  void shouldInferTextSqlTypeForInstant() {
    assertEquals("TEXT", SqlTypeResolver.resolve(Instant.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForInt() {
    assertEquals("INTEGER", SqlTypeResolver.resolve(int.class));
    assertEquals("INTEGER", SqlTypeResolver.resolve(Integer.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForLong() {
    assertEquals("INTEGER", SqlTypeResolver.resolve(long.class));
    assertEquals("INTEGER", SqlTypeResolver.resolve(Long.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForShort() {
    assertEquals("INTEGER", SqlTypeResolver.resolve(short.class));
    assertEquals("INTEGER", SqlTypeResolver.resolve(Short.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForByte() {
    assertEquals("INTEGER", SqlTypeResolver.resolve(byte.class));
    assertEquals("INTEGER", SqlTypeResolver.resolve(Byte.class));
  }

  @Test
  void shouldInferIntegerSqlTypeForBoolean() {
    assertEquals("INTEGER", SqlTypeResolver.resolve(boolean.class));
    assertEquals("INTEGER", SqlTypeResolver.resolve(Boolean.class));
  }

  @Test
  void shouldInferRealSqlTypeForDouble() {
    assertEquals("REAL", SqlTypeResolver.resolve(double.class));
    assertEquals("REAL", SqlTypeResolver.resolve(Double.class));
  }

  @Test
  void shouldInferRealSqlTypeForFloat() {
    assertEquals("REAL", SqlTypeResolver.resolve(float.class));
    assertEquals("REAL", SqlTypeResolver.resolve(Float.class));
  }

  @Test
  void shouldInferBlobSqlTypeForByteArray() {
    assertEquals("BLOB", SqlTypeResolver.resolve(byte[].class));
  }

  @Test
  void shouldInferTextSqlTypeForEnum() {
    assertEquals("TEXT", SqlTypeResolver.resolve(SampleEnum.class));
  }

  @Test
  void shouldInferTextForNull() {
    assertEquals("TEXT", SqlTypeResolver.resolve(null));
  }

  @Test
  void shouldInferTextForUnknownTypes() {
    assertEquals("TEXT", SqlTypeResolver.resolve(TypeMapperTest.class));
  }

  enum SampleEnum {
    VALUE_A,
    VALUE_B
  }
}
