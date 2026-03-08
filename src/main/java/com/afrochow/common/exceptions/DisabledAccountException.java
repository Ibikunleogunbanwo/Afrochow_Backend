package com.afrochow.common.exceptions;

public class DisabledAccountException extends RuntimeException {
  public DisabledAccountException(String message) {
    super(message);
  }
}