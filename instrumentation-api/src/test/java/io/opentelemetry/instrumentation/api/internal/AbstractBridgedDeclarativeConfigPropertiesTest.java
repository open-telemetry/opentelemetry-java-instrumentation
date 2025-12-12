/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AbstractBridgedDeclarativeConfigPropertiesTest {
  @Test
  void toSystemProperty() {
    assertThat(
            AbstractBridgedDeclarativeConfigProperties.toSystemProperty(
                Arrays.asList("a_b", "c", "d")))
        .isEqualTo("otel.instrumentation.a-b.c.d");
    assertThat(
            AbstractBridgedDeclarativeConfigProperties.toSystemProperty(
                Arrays.asList("a_b/development", "c", "d")))
        .isEqualTo("otel.instrumentation.experimental.a-b.c.d");
    assertThat(
            AbstractBridgedDeclarativeConfigProperties.toSystemProperty(
                Arrays.asList("a_experimental_b/development", "c", "d")))
        .isEqualTo("otel.instrumentation.a-experimental-b.c.d");
  }
}
