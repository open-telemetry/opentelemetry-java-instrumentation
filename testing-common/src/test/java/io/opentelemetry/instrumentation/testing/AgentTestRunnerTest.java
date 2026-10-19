/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class AgentTestRunnerTest {

  @Test
  void expectedPeerServiceReflectsMappingConfiguration() {
    String property = "otel.instrumentation.common.peer-service-mapping";
    String previous = System.getProperty(property);
    try {
      System.setProperty(property, "127.0.0.1=test-peer-service");
      assertThat(AgentTestRunner.instance().expectedPeerService()).isEqualTo("test-peer-service");

      System.clearProperty(property);
      assertThat(AgentTestRunner.instance().expectedPeerService()).isNull();
    } finally {
      if (previous == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, previous);
      }
    }
  }
}
