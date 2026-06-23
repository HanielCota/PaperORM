package com.github.paperorm.repository;

import com.github.paperorm.PaperOrm;
import java.util.Objects;

public abstract class AbstractRepository<T> extends ForwardingRepository<T> {

  protected AbstractRepository(Class<T> entityClass, PaperOrm orm) {
    super(
        Objects.requireNonNull(orm, "PaperOrm instance cannot be null").getRepository(entityClass));
  }
}
