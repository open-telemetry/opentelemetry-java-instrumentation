/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7.client;

import io.opentelemetry.instrumentation.ratpack.client.AbstractRatpackPooledHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;

class RatpackPooledHttpClientTest extends AbstractRatpackPooledHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  @Override
  protected boolean useNettyClientAttributes() {
    return false;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setClientSpanErrorMapper(RatpackTestUtils::ratpackClientSpanErrorMapper);
  }
}
