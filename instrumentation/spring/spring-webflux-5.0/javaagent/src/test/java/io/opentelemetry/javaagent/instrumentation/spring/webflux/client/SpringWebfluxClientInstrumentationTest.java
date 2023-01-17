/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.client;

import io.opentelemetry.instrumentation.spring.webflux.client.AbstractSpringWebfluxClientInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

class SpringWebfluxClientInstrumentationTest
    extends AbstractSpringWebfluxClientInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  protected WebClient.Builder instrument(WebClient.Builder builder) {
    return builder;
  }
}
