package com.github.paperorm.mapping;

import static org.junit.jupiter.api.Assertions.*;

import com.github.paperorm.database.SqliteDatabaseConnection;
import com.github.paperorm.exception.TypeConversionException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeMapperTest {

  @TempDir Path tempDir;

  private SqliteDatabaseConnection connection;
  private TypeMapper typeMapper;

  @BeforeEach
  void setUp() {
    connection = new SqliteDatabaseConnection(tempDir.resolve("test.db"));
    typeMapper = new TypeMapper();
  }

  @AfterEach
  void tearDown() {
    connection.close();
  }

  // -------------------- resolveSqlType --------------------

  @Test
  void shouldResolveTextForString() {
    assertEquals("TEXT", typeMapper.resolveSqlType(String.class));
  }

  @Test
  void shouldResolveTextForUuid() {
    assertEquals("TEXT", typeMapper.resolveSqlType(UUID.class));
  }

  @Test
  void shouldResolveTextForBigDecimal() {
    assertEquals("TEXT", typeMapper.resolveSqlType(BigDecimal.class));
  }

  @Test
  void shouldResolveTextForLocalDateTime() {
    assertEquals("TEXT", typeMapper.resolveSqlType(LocalDateTime.class));
  }

  @Test
  void shouldResolveTextForInstant() {
    assertEquals("TEXT", typeMapper.resolveSqlType(Instant.class));
  }

  @Test
  void shouldResolveIntegerForInt() {
    assertEquals("INTEGER", typeMapper.resolveSqlType(int.class));
    assertEquals("INTEGER", typeMapper.resolveSqlType(Integer.class));
  }

  @Test
  void shouldResolveIntegerForLong() {
    assertEquals("INTEGER", typeMapper.resolveSqlType(long.class));
    assertEquals("INTEGER", typeMapper.resolveSqlType(Long.class));
  }

  @Test
  void shouldResolveIntegerForShort() {
    assertEquals("INTEGER", typeMapper.resolveSqlType(short.class));
    assertEquals("INTEGER", typeMapper.resolveSqlType(Short.class));
  }

  @Test
  void shouldResolveIntegerForByte() {
    assertEquals("INTEGER", typeMapper.resolveSqlType(byte.class));
    assertEquals("INTEGER", typeMapper.resolveSqlType(Byte.class));
  }

  @Test
  void shouldResolveIntegerForBoolean() {
    assertEquals("INTEGER", typeMapper.resolveSqlType(boolean.class));
    assertEquals("INTEGER", typeMapper.resolveSqlType(Boolean.class));
  }

  @Test
  void shouldResolveRealForDouble() {
    assertEquals("REAL", typeMapper.resolveSqlType(double.class));
    assertEquals("REAL", typeMapper.resolveSqlType(Double.class));
  }

  @Test
  void shouldResolveRealForFloat() {
    assertEquals("REAL", typeMapper.resolveSqlType(float.class));
    assertEquals("REAL", typeMapper.resolveSqlType(Float.class));
  }

  @Test
  void shouldResolveBlobForByteArray() {
    assertEquals("BLOB", typeMapper.resolveSqlType(byte[].class));
  }

  @Test
  void shouldResolveTextForEnum() {
    assertEquals("TEXT", typeMapper.resolveSqlType(SampleEnum.class));
  }

  @Test
  void shouldResolveTextForNull() {
    assertEquals("TEXT", typeMapper.resolveSqlType(null));
  }

  @Test
  void shouldResolveTextForUnknownTypes() {
    assertEquals("TEXT", typeMapper.resolveSqlType(TypeMapperTest.class));
  }

  // -------------------- setParameter / readColumnValue --------------------

  @Test
  void shouldRoundTripString() throws Exception {
    connection.execute("CREATE TABLE test_str (val TEXT)");
    connection.execute("INSERT INTO test_str (val) VALUES ('hello')");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_str");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals("hello", rs.getString(1));
    }
  }

  @Test
  void shouldSetParameterAndReadColumnForInt() throws Exception {
    connection.execute("CREATE TABLE test_int (val INTEGER)");

    try (var conn = connection.openConnection();
        var stmt =
            conn.prepareStatement(
                "INSERT INTO test_int (val) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
      typeMapper.setParameter(stmt, 1, 42);
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_int");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(42, rs.getInt(1));
    }
  }

  @Test
  void shouldSetNullParameter() throws Exception {
    connection.execute("CREATE TABLE test_null (val TEXT)");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("INSERT INTO test_null (val) VALUES (?)")) {
      typeMapper.setParameter(stmt, 1, null);
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_null");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertNull(rs.getString(1));
    }
  }

  @Test
  void shouldRoundTripBoolean() throws Exception {
    connection.execute("CREATE TABLE test_bool (val INTEGER)");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("INSERT INTO test_bool (val) VALUES (?)")) {
      typeMapper.setParameter(stmt, 1, true);
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_bool");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
    }
  }

  @Test
  void shouldRoundTripUuid() throws Exception {
    connection.execute("CREATE TABLE test_uuid (val TEXT)");
    var uuid = UUID.randomUUID();

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("INSERT INTO test_uuid (val) VALUES (?)")) {
      typeMapper.setParameter(stmt, 1, uuid);
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_uuid");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(uuid.toString(), rs.getString(1));
    }
  }

  @Test
  void shouldRoundTripBigDecimal() throws Exception {
    connection.execute("CREATE TABLE test_decimal (val TEXT)");
    var value = new BigDecimal("123.456");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("INSERT INTO test_decimal (val) VALUES (?)")) {
      typeMapper.setParameter(stmt, 1, value);
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_decimal");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(value.toPlainString(), rs.getString(1));
    }
  }

  @Test
  void shouldRoundTripEnum() throws Exception {
    connection.execute("CREATE TABLE test_enum (val TEXT)");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("INSERT INTO test_enum (val) VALUES (?)")) {
      typeMapper.setParameter(stmt, 1, SampleEnum.VALUE_A);
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_enum");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals("VALUE_A", rs.getString(1));
    }
  }

  @Test
  void shouldRoundTripByteArray() throws Exception {
    connection.execute("CREATE TABLE test_blob (val BLOB)");
    var data = new byte[] {1, 2, 3, 4};

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("INSERT INTO test_blob (val) VALUES (?)")) {
      typeMapper.setParameter(stmt, 1, data);
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_blob");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertArrayEquals(data, rs.getBytes(1));
    }
  }

  // -------------------- custom converter --------------------

  @Test
  void shouldUseCustomConverter() throws Exception {
    connection.execute("CREATE TABLE test_custom (val TEXT)");
    var converter =
        new TypeConverter<String>() {
          @Override
          public Class<String> getType() {
            return String.class;
          }

          @Override
          public void setParameter(java.sql.PreparedStatement statement, int index, String value)
              throws SQLException {
            statement.setString(index, "custom:" + value);
          }

          @Override
          public String readValue(java.sql.ResultSet resultSet, String columnName)
              throws SQLException {
            var raw = resultSet.getString(columnName);
            return raw == null ? null : raw.substring("custom:".length());
          }

          @Override
          public String getSqlType() {
            return "TEXT";
          }
        };
    typeMapper.registerConverter(converter);

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("INSERT INTO test_custom (val) VALUES (?)")) {
      typeMapper.setParameter(stmt, 1, "hello");
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_custom");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals("custom:hello", rs.getString(1));
    }
  }

  @Test
  void shouldInvalidateCacheOnRegisterConverter() throws Exception {
    connection.execute("CREATE TABLE test_cache (val TEXT)");

    var converter =
        new TypeConverter<String>() {
          @Override
          public Class<String> getType() {
            return String.class;
          }

          @Override
          public void setParameter(java.sql.PreparedStatement statement, int index, String value)
              throws SQLException {
            statement.setString(index, "cached:" + value);
          }

          @Override
          public String readValue(java.sql.ResultSet resultSet, String columnName)
              throws SQLException {
            return null;
          }

          @Override
          public String getSqlType() {
            return "TEXT";
          }
        };
    typeMapper.registerConverter(converter);

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("INSERT INTO test_cache (val) VALUES (?)")) {
      typeMapper.setParameter(stmt, 1, "test");
      stmt.executeUpdate();
    }

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_cache");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals("cached:test", rs.getString(1));
    }
  }

  @Test
  void shouldResolveCustomSqlType() {
    var converter =
        new TypeConverter<CustomType>() {
          @Override
          public Class<CustomType> getType() {
            return CustomType.class;
          }

          @Override
          public void setParameter(
              java.sql.PreparedStatement statement, int index, CustomType value) {}

          @Override
          public CustomType readValue(java.sql.ResultSet resultSet, String columnName) {
            return null;
          }

          @Override
          public String getSqlType() {
            return "BLOB";
          }
        };
    typeMapper.registerConverter(converter);
    assertEquals("BLOB", typeMapper.resolveSqlType(CustomType.class));
  }

  @Test
  void shouldResolveCustomSqlTypeByRegisterSqlType() {
    typeMapper.registerSqlType(CustomType.class, "REAL");
    assertEquals("REAL", typeMapper.resolveSqlType(CustomType.class));
  }

  static class CustomType {}

  // -------------------- readColumnValue --------------------

  @Test
  void shouldReadColumnValueForInteger() throws Exception {
    connection.execute("CREATE TABLE test_read_int (val INTEGER)");
    connection.execute("INSERT INTO test_read_int (val) VALUES (99)");

    var field = ReadEntity.class.getDeclaredField("intValue");
    var column =
        new ColumnMetadata(
            "val", field, false, false, true, false, 255, false, null, false, "", "INTEGER");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_read_int");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(99, typeMapper.readColumnValue(rs, column));
    }
  }

  @Test
  void shouldReadColumnValueForString() throws Exception {
    connection.execute("CREATE TABLE test_read_str (val TEXT)");
    connection.execute("INSERT INTO test_read_str (val) VALUES ('readme')");

    var field = ReadEntity.class.getDeclaredField("strValue");
    var column =
        new ColumnMetadata(
            "val", field, false, false, true, false, 255, false, null, false, "", "TEXT");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_read_str");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals("readme", typeMapper.readColumnValue(rs, column));
    }
  }

  @Test
  void shouldReadColumnValueForNull() throws Exception {
    connection.execute("CREATE TABLE test_read_null (val TEXT)");
    connection.execute("INSERT INTO test_read_null (val) VALUES (NULL)");

    var field = ReadEntity.class.getDeclaredField("strValue");
    var column =
        new ColumnMetadata(
            "val", field, false, false, true, false, 255, false, null, false, "", "TEXT");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_read_null");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertNull(typeMapper.readColumnValue(rs, column));
    }
  }

  @Test
  void shouldReadColumnValueForEnum() throws Exception {
    connection.execute("CREATE TABLE test_read_enum (val TEXT)");
    connection.execute("INSERT INTO test_read_enum (val) VALUES ('VALUE_B')");

    var field = ReadEntity.class.getDeclaredField("enumValue");
    var column =
        new ColumnMetadata(
            "val", field, false, false, true, false, 255, false, null, false, "", "TEXT");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_read_enum");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals(SampleEnum.VALUE_B, typeMapper.readColumnValue(rs, column));
    }
  }

  @Test
  void shouldReadColumnValueForNullEnum() throws Exception {
    connection.execute("CREATE TABLE test_read_null_enum (val TEXT)");
    connection.execute("INSERT INTO test_read_null_enum (val) VALUES (NULL)");

    var field = ReadEntity.class.getDeclaredField("enumValue");
    var column =
        new ColumnMetadata(
            "val", field, false, false, true, false, 255, false, null, false, "", "TEXT");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_read_null_enum");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertNull(typeMapper.readColumnValue(rs, column));
    }
  }

  @Test
  void shouldThrowForInvalidEnumName() throws Exception {
    connection.execute("CREATE TABLE test_bad_enum (val TEXT)");
    connection.execute("INSERT INTO test_bad_enum (val) VALUES ('INVALID')");

    var field = ReadEntity.class.getDeclaredField("enumValue");
    var column =
        new ColumnMetadata(
            "val", field, false, false, true, false, 255, false, null, false, "", "TEXT");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_bad_enum");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertThrows(TypeConversionException.class, () -> typeMapper.readColumnValue(rs, column));
    }
  }

  @Test
  void shouldUseCustomConverterForReadColumnValue() throws Exception {
    connection.execute("CREATE TABLE test_custom_read (val TEXT)");
    connection.execute("INSERT INTO test_custom_read (val) VALUES ('hello')");

    var converter =
        new TypeConverter<String>() {
          @Override
          public Class<String> getType() {
            return String.class;
          }

          @Override
          public void setParameter(java.sql.PreparedStatement statement, int index, String value)
              throws SQLException {}

          @Override
          public String readValue(java.sql.ResultSet resultSet, String columnName)
              throws SQLException {
            return "overridden";
          }
        };
    typeMapper.registerConverter(converter);

    var field = ReadEntity.class.getDeclaredField("strValue");
    var column =
        new ColumnMetadata(
            "val", field, false, false, true, false, 255, false, null, false, "", "TEXT");

    try (var conn = connection.openConnection();
        var stmt = conn.prepareStatement("SELECT val FROM test_custom_read");
        var rs = stmt.executeQuery()) {
      assertTrue(rs.next());
      assertEquals("overridden", typeMapper.readColumnValue(rs, column));
    }
  }

  // -------------------- helpers --------------------

  enum SampleEnum {
    VALUE_A,
    VALUE_B
  }

  public static class ReadEntity {
    @SuppressWarnings("unused")
    private int intValue;

    @SuppressWarnings("unused")
    private String strValue;

    @SuppressWarnings("unused")
    private SampleEnum enumValue;
  }
}
