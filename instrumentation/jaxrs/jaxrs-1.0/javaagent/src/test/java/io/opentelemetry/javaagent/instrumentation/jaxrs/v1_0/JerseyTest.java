/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.spi.container.servlet.ServletContainer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.ws.rs.core.Application;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JerseyTest extends AbstractHttpServerUsingTest<Server> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @BeforeAll
  protected void setUp() {
    startServer();
  }

  @AfterAll
  protected void cleanUp() {
    cleanupServer();
  }

  @Override
  protected Server setupServer() throws Exception {
    ServletContainer servlet =
        new ServletContainer(
            new Application() {
              @Override
              public Set<Object> getSingletons() {
                Set<Object> objects = new HashSet<>();
                objects.add(new Resource.Test1());
                objects.add(new Resource.Test2());
                objects.add(new Resource.Test3());
                return objects;
              }
            });

    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    handler.setContextPath(getContextPath());
    handler.addServlet(new ServletHolder(servlet), "/*");

    Server server = new Server(port);
    server.setHandler(handler);
    server.start();

    return server;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  protected String getContextPath() {
    return "/jetty-context";
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of("test/hello/bob", "/test/hello/{name}", "Test1", "hello", "Test1 bob!"),
        Arguments.of("test2/hello/bob", "/test2/hello/{name}", "Test2", "hello", "Test2 bob!"),
        Arguments.of("test3/hi/bob", "/test3/hi/{name}", "Test3", "hello", "Test3 bob!"),
        Arguments.of("test3/nested", "/test3/nested", "Test3", "nested", "Test3 nested!"));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("provideArguments")
  void request(
      String resource,
      String expectedRoute,
      String className,
      String methodName,
      String expectedResponse) {
    AggregatedHttpResponse response =
        client.post(address.resolve(resource).toString(), "bob").aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(expectedResponse);

    String controllerName = className + "." + methodName;
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST " + getContextPath() + expectedRoute)
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(HTTP_ROUTE, getContextPath() + expectedRoute)),
                span ->
                    span.hasName(controllerName)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_NAMESPACE, Resource.class.getName() + "$" + className),
                            equalTo(CODE_FUNCTION, methodName))));
  }
}
