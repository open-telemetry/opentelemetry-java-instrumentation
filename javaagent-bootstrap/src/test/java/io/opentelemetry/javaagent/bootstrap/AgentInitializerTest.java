/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentInitializerTest {
  @AfterEach
  void tearDown() {
    System.clearProperty("foo");
    System.clearProperty("bar");
  }

  @Test
  void agentArgsSystemProperties() {
    AgentInitializer.setSystemProperties("foo=bar=baz;bar=b,c");

    assertThat(System.getProperty("foo")).isEqualTo("bar=baz");
    assertThat(System.getProperty("bar")).isEqualTo("b,c");
  }
}
