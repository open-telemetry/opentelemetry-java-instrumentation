/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0;

import static akka.http.javadsl.server.PathMatchers.integerSegment;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.javadsl.Flow;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AkkaHttpServerJavaRouteTest extends AllDirectives {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final WebClient client = WebClient.of();

  @Test
  void testMalformedRequestTarget() throws Exception {
    ActorSystem system = ActorSystem.create("malformed-request");
    int port = PortUtils.findOpenPort();
    try {
      ServerBinding binding =
          Http.get(system)
              .newServerAt("localhost", port)
              .bind(complete("ok"))
              .toCompletableFuture()
              .get(10, SECONDS);
      try {
        assertBadRequest(port, "GET /%% HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("HTTP")
                            .hasKind(SpanKind.SERVER)
                            .hasNoParent()
                            .hasStatus(StatusData.unset())
                            .hasAttributesSatisfyingExactly(
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 400))));
      } finally {
        binding.unbind().toCompletableFuture().get(10, SECONDS);
      }
    } finally {
      system.terminate();
    }
  }

  @Test
  void testMalformedRequestEntityDoesNotCreateExtraSpan() throws Exception {
    ActorSystem system = ActorSystem.create("malformed-entity");
    int port = PortUtils.findOpenPort();
    try {
      ServerBinding binding =
          Http.get(system)
              .newServerAt("localhost", port)
              .bindFlow(
                  Flow.of(HttpRequest.class)
                      .flatMapConcat(
                          request ->
                              request.entity().getDataBytes().map(bytes -> HttpResponse.create())))
              .toCompletableFuture()
              .get(10, SECONDS);
      try {
        assertBadRequest(
            port,
            "POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n"
                + "Connection: close\r\n\r\ninvalid-chunk-size\r\n");

        testing.waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("POST").hasKind(SpanKind.SERVER).hasNoParent()));
      } finally {
        binding.unbind().toCompletableFuture().get(10, SECONDS);
      }
    } finally {
      system.terminate();
    }
  }

  private static void assertBadRequest(int port, String request) throws IOException {
    try (Socket socket = new Socket("localhost", port)) {
      socket.setSoTimeout(10000);
      socket.getOutputStream().write(request.getBytes(US_ASCII));
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
      assertThat(reader.readLine()).isEqualTo("HTTP/1.1 400 Bad Request");
      while (reader.readLine() != null) {}
    }
  }

  @Test
  void testRoute() {
    ActorSystem system = ActorSystem.create("my-system");
    int port = PortUtils.findOpenPort();
    Http http = Http.get(system);

    Route route =
        concat(
            pathEndOrSingleSlash(() -> complete("root")),
            pathPrefix(
                "test",
                () ->
                    concat(
                        pathSingleSlash(() -> complete("test")),
                        path(integerSegment(), (i) -> complete("ok")))));

    CompletionStage<ServerBinding> binding = http.newServerAt("localhost", port).bind(route);
    try {
      AggregatedHttpRequest request =
          AggregatedHttpRequest.of(HttpMethod.GET, "h1c://localhost:" + port + "/test/1");
      AggregatedHttpResponse response = client.execute(request).aggregate().join();

      assertThat(response.status().code()).isEqualTo(200);
      assertThat(response.contentUtf8()).isEqualTo("ok");

      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("GET /test/*")));
    } finally {
      binding.thenCompose(ServerBinding::unbind).thenAccept(unbound -> system.terminate());
    }
  }
}
