package io.opentelemetry.smoketest.foo;

import org.junit.jupiter.api.Test;

public abstract class BaseTest {
  @Test
  void name() {
      throw new IllegalStateException("boom");

  }
}
