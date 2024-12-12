/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import java.util.Collections;
import org.junit.jupiter.api.extension.RegisterExtension;
import ratpack.func.Action;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpClientSpec;

@SuppressWarnings("deprecation") // testing deprecated API
class RatpackHttpClientOldTest extends AbstractRatpackHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  protected HttpClient buildHttpClient() throws Exception {
    return RatpackTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedClientRequestHeaders(
            Collections.singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
        .setCapturedClientResponseHeaders(
            Collections.singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
        .build()
        .instrumentHttpClient(HttpClient.of(Action.noop()));
  }

  @Override
  protected HttpClient buildHttpClient(Action<? super HttpClientSpec> action) throws Exception {
    return RatpackClientTelemetry.create(testing.getOpenTelemetry())
        .instrument(HttpClient.of(action));
  }
}
