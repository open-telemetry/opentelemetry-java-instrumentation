/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.ConfigurableApplicationContext;

class WebMvcHttpServerTest extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  private static final String CONTEXT_PATH = "/test";

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected ConfigurableApplicationContext setupServer() {
    return TestWebSpringBootApp.start(port, CONTEXT_PATH);
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext applicationContext) {
    applicationContext.close();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setContextPath(CONTEXT_PATH);
    options.setTestPathParam(true);
    // servlet filters don't capture exceptions thrown in controllers
    options.setTestException(false);

    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (endpoint == ServerEndpoint.PATH_PARAM) {
            return CONTEXT_PATH + "/path/{id}/param";
          }
          if (HttpConstants._OTHER.equals(method)) {
            return CONTEXT_PATH + endpoint.getPath();
          }
          return expectedHttpRoute(endpoint, method);
        });
  }

  @Test
  void asyncRequest() {
    ServerEndpoint endpoint = TestWebSpringBootApp.ASYNC_ENDPOINT;
    String method = "GET";
    AggregatedHttpRequest request = request(endpoint, method);
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(endpoint.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(endpoint.getBody());

    String spanId = assertResponseHasCustomizedHeaders(response, endpoint, null);
    assertTheTraces(1, null, null, spanId, method, endpoint);
  }
}
