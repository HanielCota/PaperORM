package com.github.paperorm.migration;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.exception.ConnectionException;
import com.github.paperorm.exception.OrmException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class MigrationRunner {

  private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)\\.sql$");

  private final SqlDialect dialect;

  public static List<Migration> loadFromClasspath(ClassLoader classLoader, String directoryPath) {
    Objects.requireNonNull(classLoader, "classLoader cannot be null");
    Objects.requireNonNull(directoryPath, "directoryPath cannot be null");

    var prefix = directoryPath.endsWith("/") ? directoryPath : directoryPath + "/";
    var resources = listClasspathResources(classLoader, prefix);

    return resources.stream()
        .map(r -> loadClasspathMigration(classLoader, prefix, r))
        .sorted(Comparator.comparingInt(Migration::version))
        .toList();
  }

  private static List<String> listClasspathResources(ClassLoader classLoader, String prefix) {
    var resources = new ArrayList<String>();
    try {
      Enumeration<URL> urls = classLoader.getResources(prefix);
      while (urls.hasMoreElements()) {
        var url = urls.nextElement();
        collectResourcesFromUrl(url, prefix, resources);
      }
    } catch (IOException e) {
      throw new OrmException("Failed to list migration resources in " + prefix, e);
    }
    return resources;
  }

  private static void collectResourcesFromUrl(URL url, String prefix, List<String> resources) {
    try {
      if ("jar".equals(url.getProtocol())) {
        var jarConnection = (JarURLConnection) url.openConnection();
        try (JarFile jarFile = jarConnection.getJarFile()) {
          var entries = jarFile.entries();
          while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            var name = entry.getName();
            if (name.startsWith(prefix) && !entry.isDirectory()) {
              var matcher = VERSION_PATTERN.matcher(name.substring(prefix.length()));
              if (matcher.matches()) {
                resources.add(name.substring(prefix.length()));
              }
            }
          }
        }
        return;
      }

      var dir = Path.of(url.toURI());
      try (var stream = Files.list(dir)) {
        stream
            .filter(Files::isRegularFile)
            .map(p -> p.getFileName().toString())
            .filter(name -> VERSION_PATTERN.matcher(name).matches())
            .forEach(resources::add);
      }
    } catch (Exception e) {
      throw new OrmException("Failed to collect migrations from " + url, e);
    }
  }

  private static Migration loadClasspathMigration(
      ClassLoader classLoader, String prefix, String fileName) {
    var matcher = VERSION_PATTERN.matcher(fileName);
    if (!matcher.matches()) {
      throw new OrmException("Invalid migration file name: " + fileName);
    }
    var version = Integer.parseInt(matcher.group(1));
    var resourcePath = prefix + fileName;

    try (var is = classLoader.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new OrmException("Migration resource not found: " + resourcePath);
      }
      var sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      return new Migration(version, "Migration V" + version, sql);
    } catch (IOException e) {
      throw new OrmException("Failed to read migration resource: " + resourcePath, e);
    }
  }

  public static List<Migration> loadFromDirectory(Path directory) {
    Objects.requireNonNull(directory, "directory cannot be null");
    if (!Files.isDirectory(directory)) {
      return Collections.emptyList();
    }

    var migrations = new ArrayList<Migration>();
    try (var stream = Files.list(directory)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(
              file -> {
                var matcher = VERSION_PATTERN.matcher(file.getFileName().toString());
                if (!matcher.matches()) {
                  return;
                }
                try {
                  var version = Integer.parseInt(matcher.group(1));
                  var sql = Files.readString(file, StandardCharsets.UTF_8);
                  migrations.add(new Migration(version, "Migration V" + version, sql));
                } catch (IOException e) {
                  throw new OrmException("Failed to read migration file: " + file, e);
                }
              });
    } catch (IOException e) {
      throw new OrmException("Failed to list migration files in " + directory, e);
    }

    return migrations.stream().sorted(Comparator.comparingInt(Migration::version)).toList();
  }

  public void run(DatabaseConnection connection, List<Migration> migrations) {
    Objects.requireNonNull(connection, "connection cannot be null");
    Objects.requireNonNull(migrations, "migrations cannot be null");

    ensureMigrationTable(connection);

    var appliedVersions = loadAppliedVersions(connection);
    var pendingMigrations =
        migrations.stream()
            .filter(m -> !appliedVersions.contains(m.version()))
            .sorted(Comparator.comparingInt(Migration::version))
            .toList();

    if (pendingMigrations.isEmpty()) {
      return;
    }

    connection.runInTransaction(
        txConnection -> {
          try {
            if (!this.dialect.acquireMigrationLock(txConnection)) {
              throw new ConnectionException(
                  "Could not acquire migration lock. Another process may be running migrations.");
            }
            for (var migration : pendingMigrations) {
              applyMigration(txConnection, migration);
            }
          } catch (SQLException e) {
            throw new ConnectionException("Migration failed", e);
          } finally {
            try {
              this.dialect.releaseMigrationLock(txConnection);
            } catch (SQLException ignored) {
              // release lock is best-effort
            }
          }
          return null;
        });
  }

  private void ensureMigrationTable(DatabaseConnection connection) {
    var sql = this.dialect.createMigrationTable();
    try {
      connection.execute(sql);
    } catch (SQLException e) {
      throw new ConnectionException("Failed to create migration table", e);
    }
  }

  private Set<Integer> loadAppliedVersions(DatabaseConnection connection) {
    var tableName = this.dialect.quoteIdentifier("paper_orm_migrations");
    var sql = "SELECT version FROM " + tableName;
    var versions = new HashSet<Integer>();

    try (var conn = connection.openConnection();
        var stmt = conn.createStatement();
        var resultSet = stmt.executeQuery(sql)) { // NOSONAR: tableName is a quoted constant

      while (resultSet.next()) {
        versions.add(resultSet.getInt("version"));
      }
    } catch (SQLException e) {
      throw new ConnectionException("Failed to load applied migrations", e);
    }

    return versions;
  }

  private void applyMigration(Connection txConnection, Migration migration) throws SQLException {
    var tableName = this.dialect.quoteIdentifier("paper_orm_migrations");
    var insertSql = "INSERT INTO " + tableName + " (version, description) VALUES (?, ?)";

    var statements = splitStatements(migration.sql());
    try (var statement = txConnection.createStatement()) {
      for (var sql : statements) {
        statement.execute(sql);
      }
    }

    try (var statement =
        txConnection.prepareStatement(insertSql)) { // NOSONAR: tableName is a quoted constant
      statement.setInt(1, migration.version());
      statement.setString(2, migration.description());
      statement.executeUpdate();
    }
  }

  static List<String> splitStatements(String script) {
    if (script == null || script.isBlank()) {
      return List.of();
    }

    var statements = new ArrayList<String>();
    var current = new StringBuilder();
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;

    for (var i = 0; i < script.length(); i++) {
      var ch = script.charAt(i);
      current.append(ch);

      if (ch == '\'' && !inDoubleQuote) {
        inSingleQuote = !inSingleQuote;
      } else if (ch == '"' && !inSingleQuote) {
        inDoubleQuote = !inDoubleQuote;
      }

      if (ch == ';' && !inSingleQuote && !inDoubleQuote) {
        var trimmed = current.toString().trim();
        if (!trimmed.isEmpty()) {
          statements.add(trimmed.substring(0, trimmed.length() - 1).trim());
        }
        current.setLength(0);
      }
    }

    var trimmed = current.toString().trim();
    if (!trimmed.isEmpty()) {
      statements.add(trimmed);
    }

    return statements;
  }
}
