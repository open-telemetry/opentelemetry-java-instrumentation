/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static io.opentelemetry.semconv.ResourceAttributes.SERVICE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestBootInfClassesResource {
  @Mock ConfigProperties config;

  @Test
  void testServiceName() {
    // verify that the test app, that is added as a dependency to this project, has the expected
    // layout
    assertThat(getClass().getResource("/application.properties")).isNull();
    assertThat(getClass().getResource("/BOOT-INF/classes/application.properties")).isNotNull();

    SpringBootServiceNameDetector guesser = new SpringBootServiceNameDetector();
    Resource result = guesser.createResource(config);
    assertThat(result.getAttribute(SERVICE_NAME)).isEqualTo("otel-spring-test-app");
  }
}
