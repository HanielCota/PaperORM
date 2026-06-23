package com.github.paperorm.exception;

public sealed class OrmException extends RuntimeException
    permits MappingException, TypeConversionException, ConnectionException {

  public OrmException(String message) {
    super(message);
  }

  public OrmException(String message, Throwable cause) {
    super(message, cause);
  }
}
