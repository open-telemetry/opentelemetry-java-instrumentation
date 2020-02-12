package com.datadog.profiling.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

public class ConfigurationExceptionTest {

  private static final String MESSAGE = "message";

  @Test
  public void testMessageConstructor() {
    assertEquals(MESSAGE, new ConfigurationException(MESSAGE).getMessage());
  }

  @Test
  public void testCauseConstructor() {
    final Throwable cause = new RuntimeException();
    final Exception exception = new ConfigurationException(cause);
    assertSame(cause, exception.getCause());
  }
}
