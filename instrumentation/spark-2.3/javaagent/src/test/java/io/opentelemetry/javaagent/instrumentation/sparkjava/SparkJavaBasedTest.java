/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sparkjava;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import spark.Spark;

public class SparkJavaBasedTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static int port;
  static WebClient client;

  @BeforeAll
  static void setup() {
    port = PortUtils.findOpenPort();
    TestSparkJavaApplication.initSpark(port);
    client = WebClient.of("http://localhost:" + port);
  }

  @AfterAll
  static void cleanup() {
    Spark.stop();
  }

  @Test
  void generatesSpans() {
    AggregatedHttpResponse response = client.get("/param/asdf1234").aggregate().join();
    String content = response.contentUtf8();

    assertNotEquals(port, 0);
    assertEquals(content, "Hello asdf1234");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /param/:param")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.URL_SCHEME, "http"),
                            equalTo(SemanticAttributes.URL_PATH, "/param/asdf1234"),
                            equalTo(SemanticAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(
                                SemanticAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(SemanticAttributes.HTTP_ROUTE, "/param/:param"),
                            equalTo(SemanticAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(SemanticAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(SemanticAttributes.SERVER_PORT, port),
                            equalTo(SemanticAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class)))));
  }
}
