/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.spring.webflux.client.AbstractSpringWebfluxClientInstrumentationTest;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
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

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          if (uri.getPort() == PortUtils.UNUSABLE_PORT
              || uri.getHost().equals("192.0.2.1")
              || uri.getPath().endsWith("/read-timeout")) {
            attributes.remove(NETWORK_PROTOCOL_VERSION);
          }
          return attributes;
        });

    // Disable remote connection tests on Windows due to reactor-netty creating extra spans
    if (OS.WINDOWS.isCurrentOs()) {
      optionsBuilder.setTestRemoteConnection(false);
    }
  }

  @Test
  void shouldNormalizeHttp2ProtocolVersion() {
    assertThat(HttpProtocolVersion.format(2, 0)).isEqualTo("2");
  }

  @Test
  void shouldAddProtocolVersionToDurationMetric() {
    URI uri = resolveAddress("/success");
    int responseCode = sendRequest(buildRequest("GET", uri, emptyMap()), "GET", uri, emptyMap());

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.spring-webflux-5.0",
        metric ->
            metric
                .hasName("http.client.request.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point.hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes.asMap())
                                            .containsEntry(NETWORK_PROTOCOL_VERSION, "1.1")))));
  }
}
