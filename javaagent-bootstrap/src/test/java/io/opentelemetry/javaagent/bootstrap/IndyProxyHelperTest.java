/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IndyProxyHelperTest {

  @Test
  void wrongType() {
    assertThrows(
        IllegalArgumentException.class, () -> IndyProxyHelper.unwrapIfNeeded("", Integer.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> IndyProxyHelper.unwrapIfNeeded(proxy(""), Integer.class));
  }

  @Test
  void unwrap() {

    // no wrapping
    Number number = IndyProxyHelper.unwrapIfNeeded(42, Number.class);
    assertThat(number).isEqualTo(42);

    // unwrap needed
    String string = IndyProxyHelper.unwrapIfNeeded(proxy("hello"), String.class);
    assertThat(string).isEqualTo("hello");
  }

  private static IndyProxy proxy(Object delegate) {
    return () -> delegate;
  }
}
