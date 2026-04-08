/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.spring.webflux.client.AbstractSpringWebfluxClientInstrumentationTest;
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
  protected WebClient.Builder instrument(WebClient.Builder builder) {
    SpringWebfluxClientTelemetry instrumentation =
        SpringWebfluxClientTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedRequestHeaders(singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
            .setCapturedResponseHeaders(singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
            .build();
    return builder.filters(instrumentation::addFilterAndRegisterReactorHook);
  }
}
