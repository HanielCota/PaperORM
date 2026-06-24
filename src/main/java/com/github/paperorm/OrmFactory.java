package com.github.paperorm;

import com.github.paperorm.repository.Repository;
import com.github.paperorm.repository.SqlRepository;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class OrmFactory {

  @NonNull private final OrmContext context;

  public <T> Repository<T> createRepository(Class<T> entityClass) {
    Objects.requireNonNull(entityClass, "entityClass");
    return new SqlRepository<>(entityClass, context);
  }

  public <T> Repository<T> createRepository(Class<T> entityClass, OrmSession session) {
    Objects.requireNonNull(entityClass, "entityClass");
    Objects.requireNonNull(session, "session");
    return new SqlRepository<>(entityClass, context, session);
  }
}
