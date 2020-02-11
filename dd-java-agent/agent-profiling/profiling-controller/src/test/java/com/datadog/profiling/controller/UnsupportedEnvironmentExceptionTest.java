package com.datadog.profiling.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class UnsupportedEnvironmentExceptionTest {

  private static final String MESSAGE = "message";

  @Test
  public void testMessageConstructor() {
    assertEquals(MESSAGE, new UnsupportedEnvironmentException(MESSAGE).getMessage());
  }

  @Test
  public void testMessageCauseConstructor() {
    final Throwable cause = new RuntimeException();
    final Exception exception = new UnsupportedEnvironmentException(MESSAGE, cause);
    assertEquals(MESSAGE, exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
