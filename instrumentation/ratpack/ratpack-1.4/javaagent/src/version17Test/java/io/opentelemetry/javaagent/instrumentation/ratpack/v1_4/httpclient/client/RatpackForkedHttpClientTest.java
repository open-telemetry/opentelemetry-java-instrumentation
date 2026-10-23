/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.httpclient.client;

import io.opentelemetry.instrumentation.ratpack.client.AbstractRatpackForkedHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AgentHttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;

class RatpackForkedHttpClientTest extends AbstractRatpackForkedHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentHttpClientInstrumentationExtension.create();

  @Override
  protected boolean useNettyClientAttributes() {
    return false;
  }

  @Override
  protected boolean capturesProtocolVersion() {
    return true;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setClientSpanErrorMapper(RatpackTestUtils::ratpackClientSpanErrorMapper);
  }
}
