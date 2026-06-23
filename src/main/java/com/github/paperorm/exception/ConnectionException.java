package com.github.paperorm.exception;

public final class ConnectionException extends OrmException {

  public ConnectionException(String message) {
    super(message);
  }

  public ConnectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
