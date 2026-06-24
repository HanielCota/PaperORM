package com.github.paperorm.repository;

import com.github.paperorm.database.DatabaseConnection;
import com.github.paperorm.dialect.SqlDialect;
import com.github.paperorm.mapping.EntityScanner;
import com.github.paperorm.mapping.IdResolver;
import com.github.paperorm.mapping.TypeMapper;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface RepositoryContext {
  DatabaseConnection connection();

  EntityScanner scanner();

  SqlDialect dialect();

  TypeMapper typeMapper();

  IdResolver idResolver();

  Executor executor();

  boolean useCache();

  CompletableFuture<Void> migrationsFuture();
}
