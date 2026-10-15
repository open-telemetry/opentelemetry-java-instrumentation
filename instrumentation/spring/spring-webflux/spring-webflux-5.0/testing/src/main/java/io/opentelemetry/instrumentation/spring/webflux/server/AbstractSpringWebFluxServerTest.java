/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.StringAssertConsumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public abstract class AbstractSpringWebFluxServerTest
    extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.0";

  public static final ServerEndpoint NESTED_PATH =
      new ServerEndpoint("NESTED_PATH", "nestedPath/hello/world", 200, "nested path");

  protected abstract Class<?> getApplicationClass();

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected ConfigurableApplicationContext setupServer() {
    SpringApplication app = new SpringApplication(getApplicationClass());
    Map<String, Object> properties = new HashMap<>();
    properties.put("server.port", port);
    properties.put("server.context-path", getContextPath());
    properties.put("server.servlet.contextPath", getContextPath());
    properties.put("server.error.include-message", "always");
    app.setDefaultProperties(properties);
    return app.run();
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext configurableApplicationContext) {
    configurableApplicationContext.close();
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    if (endpoint.equals(PATH_PARAM)) {
      return getContextPath() + "/path/{id}/param";
    } else if (endpoint.equals(NOT_FOUND)) {
      return "/**";
    } else if (endpoint.equals(NESTED_PATH)) {
      return "/nestedPath/hello/world";
    }
    return super.expectedHttpRoute(endpoint, method);
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setTestPathParam(true);
    options.setHasHandlerSpan(unused -> true);
  }

  protected static void assertHandlerExceptionLog(
      StringAssertConsumer exceptionTypeAssertion, StringAssertConsumer exceptionMessageAssertion) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              List<LogRecordData> logs =
                  testing.logRecords().stream()
                      .filter(log -> "exception".equals(log.getEventName()))
                      .filter(
                          log ->
                              INSTRUMENTATION_NAME.equals(
                                  log.getInstrumentationScopeInfo().getName()))
                      .collect(toList());

              assertThat(logs).hasSize(1);
              assertThat(logs.get(0))
                  .hasSeverity(Severity.WARN)
                  .hasEventName("exception")
                  .hasAttributesSatisfyingExactly(
                      satisfies(EXCEPTION_TYPE, exceptionTypeAssertion),
                      satisfies(EXCEPTION_MESSAGE, exceptionMessageAssertion),
                      satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class)));
            });
  }
}
