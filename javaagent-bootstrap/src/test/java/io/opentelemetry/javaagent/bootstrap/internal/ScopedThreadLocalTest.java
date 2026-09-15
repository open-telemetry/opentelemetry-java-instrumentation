/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScopedThreadLocalTest {

  @Test
  void restoresNestedValues() {
    ScopedThreadLocal<String> scoped = new ScopedThreadLocal<>();

    String beforeOuter = scoped.set("outer");
    String beforeInner = scoped.set("inner");

    assertThat(scoped.get()).isEqualTo("inner");

    scoped.restore(beforeInner);
    assertThat(scoped.get()).isEqualTo("outer");

    scoped.restore(beforeOuter);
    assertThat(scoped.get()).isNull();
  }

  @Test
  void nullTemporarilyClearsValue() {
    ScopedThreadLocal<String> scoped = new ScopedThreadLocal<>();
    String beforeOuter = scoped.set("outer");

    String beforeClear = scoped.set(null);
    assertThat(scoped.get()).isNull();

    scoped.restore(beforeClear);
    assertThat(scoped.get()).isEqualTo("outer");

    scoped.restore(beforeOuter);
  }
}
