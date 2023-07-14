/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus.resteasy.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.client.ClientFactory;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.client.logging.LoggingClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class AbstractQuarkusJaxRsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static WebClient client;
  private static int port;

  @BeforeAll
  static void setUp() {
    client =
        WebClient.builder()
            .responseTimeout(Duration.ofMinutes(1))
            .writeTimeout(Duration.ofMinutes(1))
            .factory(ClientFactory.builder().connectTimeout(Duration.ofMinutes(1)).build())
            .decorator(LoggingClient.newDecorator())
            .build();
    port = Integer.parseInt(System.getProperty("quarkus.http.test-port"));
  }

  private static AggregatedHttpResponse request(String path) {
    return request(path, Collections.emptyMap());
  }

  private static AggregatedHttpResponse request(String path, Map<String, String> headers) {
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(HttpMethod.GET, "h1c://localhost:" + port + path);
    if (!headers.isEmpty()) {
      request = AggregatedHttpRequest.of(request.headers().toBuilder().add(headers).build());
    }
    return client.execute(request).aggregate().join();
  }

  @Test
  void testPathOnMethod() {
    AggregatedHttpResponse response = request("/test");
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("success");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /test").hasKind(SpanKind.SERVER)));
  }

  @Test
  void testPathOnClass() {
    AggregatedHttpResponse response = request("/hello");
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /hello").hasKind(SpanKind.SERVER)));
  }

  @Test
  void testPathParameter() {
    AggregatedHttpResponse response = request("/hello/greeting/test");
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("hello test");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /hello/greeting/{name}").hasKind(SpanKind.SERVER)));
  }

  @Test
  void testSubResourceLocator() {
    AggregatedHttpResponse response = request("/test-sub-resource-locator/call/sub");
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("success");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /test-sub-resource-locator/call/sub")
                        .hasKind(SpanKind.SERVER)));
  }

  @Test
  void testAbort() {
    AggregatedHttpResponse response = request("/hello", Collections.singletonMap("abort", "true"));
    assertThat(response.status().code()).isEqualTo(401);
    assertThat(response.contentUtf8()).isEqualTo("Aborted");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /hello").hasKind(SpanKind.SERVER)));
  }
}
