/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;

import io.opentelemetry.instrumentation.spring.webflux.client.AbstractSpringWebfluxClientInstrumentationTest;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.Experimental;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

class SpringWebfluxClientInstrumentationTest
    extends AbstractSpringWebfluxClientInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected boolean supportsUriTemplates() {
    return true;
  }

  @Override
  protected boolean includesBasePathInUriTemplate() {
    return testLatestDeps();
  }

  @Override
  protected WebClient.Builder instrument(WebClient.Builder builder) {
    SpringWebfluxClientTelemetryBuilder telemetryBuilder =
        SpringWebfluxClientTelemetry.builder(testing.getOpenTelemetry())
            .setRequestHeaders(AbstractHttpClientTest.TEST_HEADERS)
            .setResponseHeaders(AbstractHttpClientTest.TEST_HEADERS);
    Experimental.setEmitExperimentalTelemetry(telemetryBuilder, emitExperimentalTelemetry());
    SpringWebfluxClientTelemetry instrumentation = telemetryBuilder.build();
    return builder.filters(instrumentation::addFilterAndRegisterReactorHook);
  }
}
