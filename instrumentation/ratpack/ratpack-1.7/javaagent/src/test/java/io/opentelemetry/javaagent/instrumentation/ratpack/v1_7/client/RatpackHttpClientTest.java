/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7.client;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.ratpack.client.AbstractRatpackHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RatpackHttpClientTest extends AbstractRatpackHttpClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

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

  @Test
  void durationMetricHasProtocolVersion() throws Exception {
    URI uri = resolveAddress("/success");

    assertThat(sendRequest(null, "GET", uri, emptyMap())).isEqualTo(200);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.ratpack-1.7",
        metric ->
            metric
                .hasName("http.client.request.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point.hasAttributesSatisfyingExactly(
                                    equalTo(HTTP_REQUEST_METHOD, "GET"),
                                    equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                    equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                    equalTo(SERVER_ADDRESS, uri.getHost()),
                                    equalTo(SERVER_PORT, uri.getPort())))));
  }
}
