/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class TestServerServiceMappingTest {

  @Test
  void expectedMappedServiceName() {
    String property = "otel.instrumentation.common.server-service-mapping";
    String originalValue = System.getProperty(property);
    try {
      System.clearProperty(property);
      assertThat(TestServerServiceMapping.expectedMappedServiceName()).isNull();

      System.setProperty(property, "localhost=test-server-service");
      assertThat(TestServerServiceMapping.expectedMappedServiceName())
          .isEqualTo("test-server-service");
    } finally {
      if (originalValue == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, originalValue);
      }
    }
  }
}
