package com.github.paperorm.exception;

public final class TypeConversionException extends OrmException {

  public TypeConversionException(String message) {
    super(message);
  }

  public TypeConversionException(String message, Throwable cause) {
    super(message, cause);
  }
}
