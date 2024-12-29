/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InstrumentationProxyHelperTest {

  @Test
  void wrongType() {
    assertThrows(
        IllegalArgumentException.class,
        () -> InstrumentationProxyHelper.unwrapIfNeeded("", Integer.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> InstrumentationProxyHelper.unwrapIfNeeded(proxy(""), Integer.class));
  }

  @Test
  void unwrap() {

    // no wrapping
    Number number = InstrumentationProxyHelper.unwrapIfNeeded(42, Number.class);
    assertThat(number).isEqualTo(42);

    // unwrap needed
    String string = InstrumentationProxyHelper.unwrapIfNeeded(proxy("hello"), String.class);
    assertThat(string).isEqualTo("hello");
  }

  private static InstrumentationProxy proxy(Object delegate) {
    return () -> delegate;
  }
}
