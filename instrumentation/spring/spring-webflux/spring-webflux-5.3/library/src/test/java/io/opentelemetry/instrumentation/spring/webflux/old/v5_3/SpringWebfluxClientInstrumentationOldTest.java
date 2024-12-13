/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.old.v5_3;

import io.opentelemetry.instrumentation.spring.webflux.client.AbstractSpringWebfluxClientInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

@SuppressWarnings("deprecation") // testing deprecated API
class SpringWebfluxClientInstrumentationOldTest
    extends AbstractSpringWebfluxClientInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected WebClient.Builder instrument(WebClient.Builder builder) {
    io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry instrumentation =
        io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry.builder(
                testing.getOpenTelemetry())
            .setCapturedClientRequestHeaders(
                Collections.singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
            .setCapturedClientResponseHeaders(
                Collections.singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
            .build();
    return builder.filters(instrumentation::addClientTracingFilter);
  }
}
