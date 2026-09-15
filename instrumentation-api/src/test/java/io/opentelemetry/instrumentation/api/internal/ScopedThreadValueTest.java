/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScopedThreadValueTest {

  @Test
  void restoresNestedValues() {
    ScopedThreadValue<String> scoped = new ScopedThreadValue<>();

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
    ScopedThreadValue<String> scoped = new ScopedThreadValue<>();
    String beforeOuter = scoped.set("outer");

    String beforeClear = scoped.set(null);
    assertThat(scoped.get()).isNull();

    scoped.restore(beforeClear);
    assertThat(scoped.get()).isEqualTo("outer");

    scoped.restore(beforeOuter);
  }
}
