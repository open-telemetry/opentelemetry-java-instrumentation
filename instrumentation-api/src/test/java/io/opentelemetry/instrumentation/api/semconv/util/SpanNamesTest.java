/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class SpanNamesTest {
  @Test
  void testFromMethod() throws NoSuchMethodException {
    Method method = TestClass.class.getMethod("test");
    assertThat(SpanNames.fromMethod(method)).isEqualTo("TestClass.test");
  }

  static class TestClass {
    private TestClass() {}

    public void test() {}
  }
}
