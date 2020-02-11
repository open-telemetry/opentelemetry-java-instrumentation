package com.datadog.profiling.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ThreadFactory;
import org.junit.jupiter.api.Test;

public class ProfilingThreadFactoryTest {

  private final ThreadFactory factory = new ProfilingThreadFactory("test-name");

  @Test
  public void testThreadName() {
    final Thread thread = factory.newThread(() -> {});
    assertEquals("test-name", thread.getName());
  }
}
