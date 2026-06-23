package com.github.paperorm.exception;

public final class MappingException extends OrmException {

  public MappingException(String message) {
    super(message);
  }

  public MappingException(String message, Throwable cause) {
    super(message, cause);
  }
}
