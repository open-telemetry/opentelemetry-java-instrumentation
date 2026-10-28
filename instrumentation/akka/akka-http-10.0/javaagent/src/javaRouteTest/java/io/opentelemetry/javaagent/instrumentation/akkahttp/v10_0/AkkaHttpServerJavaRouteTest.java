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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.settings.ServerSettings;
import akka.stream.javadsl.Flow;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AkkaHttpServerJavaRouteTest extends AllDirectives {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final WebClient client = WebClient.of();

  @ParameterizedTest
  @MethodSource("malformedRequestTargetCases")
  void testMalformedRequestTarget(String parsingErrorHandler, int expectedStatus) throws Exception {
    ActorSystem system = ActorSystem.create("malformed-request");
    cleanup.deferCleanup(system::terminate);

    ServerSettings settings = ServerSettings.create(system);
    if (parsingErrorHandler != null) {
      settings = settings.withParsingErrorHandler(parsingErrorHandler);
    }
    ServerBinding binding =
        Http.get(system)
            .newServerAt("localhost", 0)
            .withSettings(settings)
            .bind(complete("ok"))
            .toCompletableFuture()
            .get(10, SECONDS);
    cleanup.deferCleanup(() -> binding.unbind().toCompletableFuture().get(10, SECONDS));

    int port = binding.localAddress().getPort();
    Map<String, String> responseHeaders =
        assertResponse(
            port,
            "GET /%% HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n",
            expectedStatus);
    assertThat(responseHeaders).containsKeys("X-Test-TraceId", "X-Test-SpanId");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HTTP")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_RESPONSE_STATUS_CODE, expectedStatus))
                        .hasTraceId(responseHeaders.get("X-Test-TraceId"))
                        .hasSpanId(responseHeaders.get("X-Test-SpanId"))));
  }

  private static Stream<Arguments> malformedRequestTargetCases() {
    return Stream.of(
        argumentSet("default handler", null, 400),
        argumentSet("custom handler", CustomParsingErrorHandler.class.getName(), 418));
  }

  @Test
  void testMalformedRequestEntityDoesNotCreateExtraSpan() throws Exception {
    ActorSystem system = ActorSystem.create("malformed-entity");
    cleanup.deferCleanup(system::terminate);

    ServerBinding binding =
        Http.get(system)
            .newServerAt("localhost", 0)
            .bindFlow(
                Flow.of(HttpRequest.class)
                    .flatMapConcat(
                        request ->
                            request.entity().getDataBytes().map(bytes -> HttpResponse.create())))
            .toCompletableFuture()
            .get(10, SECONDS);
    cleanup.deferCleanup(() -> binding.unbind().toCompletableFuture().get(10, SECONDS));

    int port = binding.localAddress().getPort();
    assertResponse(
        port,
        "POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n"
            + "Connection: close\r\n\r\ninvalid-chunk-size\r\n",
        400);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("POST").hasKind(SpanKind.SERVER).hasNoParent()));
  }

  private static Map<String, String> assertResponse(int port, String request, int status)
      throws IOException {
    try (Socket socket = new Socket("localhost", port)) {
      socket.setSoTimeout(10000);
      socket.getOutputStream().write(request.getBytes(US_ASCII));
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
      assertThat(reader.readLine()).startsWith("HTTP/1.1 " + status + " ");
      Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      String line;
      while ((line = reader.readLine()) != null && !line.isEmpty()) {
        int separator = line.indexOf(':');
        headers.put(line.substring(0, separator), line.substring(separator + 1).trim());
      }
      while (reader.readLine() != null) {}
      return headers;
    }
  }

  @Test
  void testRoute() throws Exception {
    ActorSystem system = ActorSystem.create("my-system");
    cleanup.deferCleanup(system::terminate);

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

    ServerBinding binding =
        http.newServerAt("localhost", 0).bind(route).toCompletableFuture().get(10, SECONDS);
    cleanup.deferCleanup(() -> binding.unbind().toCompletableFuture().get(10, SECONDS));

    int port = binding.localAddress().getPort();
    AggregatedHttpRequest request =
        AggregatedHttpRequest.of(HttpMethod.GET, "h1c://localhost:" + port + "/test/1");
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("ok");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("GET /test/*")));
  }
}
