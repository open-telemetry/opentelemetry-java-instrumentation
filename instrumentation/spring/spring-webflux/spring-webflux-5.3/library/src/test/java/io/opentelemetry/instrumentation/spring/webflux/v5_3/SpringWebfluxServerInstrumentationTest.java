/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.client.ClientRequestContext;
import io.opentelemetry.testing.internal.armeria.client.ClientRequestContextCaptor;
import io.opentelemetry.testing.internal.armeria.client.Clients;
import io.opentelemetry.testing.internal.armeria.client.ResponseCancellationException;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.ConfigurableApplicationContext;

class SpringWebfluxServerInstrumentationTest
    extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  private static final String CONTEXT_PATH = "/test";

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Override
  protected ConfigurableApplicationContext setupServer() {
    return TestWebfluxSpringBootApp.start(port, CONTEXT_PATH);
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext applicationContext) {
    applicationContext.close();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setContextPath(CONTEXT_PATH);
    options.setTestPathParam(true);

    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (endpoint == ServerEndpoint.PATH_PARAM) {
            return CONTEXT_PATH + "/path/{id}/param";
          }
          return expectedHttpRoute(endpoint, method);
        });

    options.disableTestNonStandardHttpMethod();
  }

  @Test
  void noMono() {
    ServerEndpoint endpoint = new ServerEndpoint("NO_MONO", "no-mono", 200, "success", false);
    String method = "GET";
    AggregatedHttpRequest request = request(endpoint, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    assertTheTraces(1, null, null, null, method, endpoint);
  }

  @Test
  void cancelRequest() throws InterruptedException {
    ServerEndpoint endpoint = new ServerEndpoint("CANCEL", "cancel", 0, null, false);
    String method = "GET";
    AggregatedHttpRequest request = request(endpoint, method);

    ClientRequestContextCaptor captor = Clients.newContextCaptor();
    cleanup.deferCleanup(captor);
    CompletableFuture<AggregatedHttpResponse> future =
        client.execute(request).aggregate().toCompletableFuture();
    ClientRequestContext clientRequestContext = captor.get();
    Thread.sleep(1_000);
    clientRequestContext.cancel();
    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .hasMessage(ResponseCancellationException.class.getName());

    assertTheTraces(1, null, null, null, method, endpoint);
    assertCancellationTelemetry("cancelled", null);
  }

  @Test
  void cancelCommittedResponse() throws IOException {
    try (Socket socket = new Socket("localhost", port)) {
      socket.setSoTimeout(10_000);
      socket
          .getOutputStream()
          .write(
              ("GET "
                      + CONTEXT_PATH
                      + "/cancel-after-commit HTTP/1.1\r\nHost: localhost:"
                      + port
                      + "\r\nConnection: close\r\n\r\n")
                  .getBytes(US_ASCII));
      socket.getOutputStream().flush();

      BufferedReader reader =
          new BufferedReader(new InputStreamReader(socket.getInputStream(), US_ASCII));
      assertThat(reader.readLine()).startsWith("HTTP/1.1 200");
      String line;
      do {
        line = reader.readLine();
        assertThat(line).isNotNull();
      } while (!line.isEmpty());
      assertThat(reader.readLine()).isEqualTo("9");
      assertThat(reader.readLine()).isEqualTo("committed");
    }

    assertCancellationTelemetry(null, 200);
  }

  private static void assertCancellationTelemetry(String errorType, Integer responseStatusCode) {
    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          SpanData serverSpan = trace.getSpan(0);
          assertThat(serverSpan.getKind()).isEqualTo(SpanKind.SERVER);
          assertThat(serverSpan.getStatus()).isEqualTo(StatusData.unset());
          assertThat(serverSpan.getAttributes().get(ERROR_TYPE)).isEqualTo(errorType);
          assertThat(serverSpan.getAttributes().get(HTTP_RESPONSE_STATUS_CODE))
              .isEqualTo(responseStatusCode == null ? null : responseStatusCode.longValue());
          instrumentationName.set(serverSpan.getInstrumentationScopeInfo().getName());
        });

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        metric ->
            metric
                .hasName("http.server.request.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point -> {
                              if (errorType != null) {
                                point.hasAttribute(ERROR_TYPE, errorType);
                              } else {
                                point.hasAttributesSatisfying(
                                    attributes -> {
                                      assertThat(attributes.get(HTTP_RESPONSE_STATUS_CODE))
                                          .isEqualTo(200L);
                                      assertThat(attributes.get(ERROR_TYPE)).isNull();
                                    });
                              }
                            })));
  }
}
