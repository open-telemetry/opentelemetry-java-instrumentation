/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OkHttp3Test extends AbstractOkHttp3Test {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forLibrary();

  @Override
  public Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder) {
    clientBuilder.protocols(singletonList(Protocol.HTTP_1_1));
    return OkHttpTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedRequestHeaders(singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
        .build()
        .newCallFactory(clientBuilder.build());
  }

  public Call.Factory createCallFactoryWithNetworkTiming(OkHttpClient.Builder clientBuilder) {
    clientBuilder.protocols(singletonList(Protocol.HTTP_1_1));
    return OkHttpTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedRequestHeaders(singletonList(AbstractHttpClientTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(singletonList(AbstractHttpClientTest.TEST_RESPONSE_HEADER))
        .build()
        .newCallFactoryWithNetworkTiming(clientBuilder.build());
  }

  @Test
  void networkTimingClient() throws Exception {
    okhttp3.Request request =
        new okhttp3.Request.Builder().url(resolveAddress("/success").toString()).build();
    okhttp3.Response response =
        createCallFactoryWithNetworkTiming(new OkHttpClient.Builder()).newCall(request).execute();
    assertThat(response.code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> {
                assertClientSpan(span, resolveAddress("/success"), "GET", 200, null)
                    .hasNoParent()
                    .hasAttributesSatisfying(
                        attrs -> {
                          boolean hasTiming =
                              attrs.asMap().keySet().stream()
                                  .map(AttributeKey::getKey)
                                  .anyMatch(
                                      k -> k.endsWith(".start_time") || k.endsWith(".end_time"));
                          assertThat(hasTiming).isTrue();
                        });
              },
              span -> assertServerSpan(span).hasParent(trace.getSpan(0)));
        });
  }
}
