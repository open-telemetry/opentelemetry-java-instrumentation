/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbeddedInstrumentationPropertiesTest {

  @Test
  void loadVersionFromClass() {
    assertThat(
            EmbeddedInstrumentationProperties.loadVersionFromClass("io.opentelemetry.okhttp-3.0"))
        .isNotEmpty();
    assertThat(EmbeddedInstrumentationProperties.loadVersionFromClass("does-not-exist")).isNull();
  }

  @Test
  void loadVersionFromProperties() {
    assertThat(
            EmbeddedInstrumentationProperties.loadVersionFromProperties(
                "io.opentelemetry.okhttp-3.0"))
        .isNotEmpty();
    assertThat(EmbeddedInstrumentationProperties.loadVersionFromProperties("does-not-exist"))
        .isNull();
  }
}
