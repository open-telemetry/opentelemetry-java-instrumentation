/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.ConfigurableApplicationContext;

public final class SpringWebfluxServerInstrumentationTest
    extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  private static final String CONTEXT_PATH = "/test";

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected ConfigurableApplicationContext setupServer() {
    return TestWebfluxSpringBootApp.start(port, CONTEXT_PATH);
  }

  @Override
  public void stopServer(ConfigurableApplicationContext applicationContext) {
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
    ServerEndpoint endpoint = new ServerEndpoint("NO_MONO", "no-mono", 200, "success");
    String method = "GET";
    AggregatedHttpRequest request = request(endpoint, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUCCESS.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUCCESS.getBody());

    assertTheTraces(1, null, null, null, method, endpoint);
  }

  @Test
  void cancelledRequestRecordsServerSpan() throws InterruptedException {
    ServerEndpoint endpoint = new ServerEndpoint("NEVER", "never", 200, "never");
    CompletableFuture<AggregatedHttpResponse> future =
        client.execute(request(endpoint, "GET")).aggregate().toCompletableFuture();
    Thread.sleep(200);
    future.cancel(true);
    Thread.sleep(500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasKind(SpanKind.SERVER).hasAttribute(HTTP_REQUEST_METHOD, "GET")));
  }
}
