/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.LOGIN;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.extension.RegisterExtension;

class TomcatHandlerTest extends AbstractHttpServerTest<Tomcat> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private static final List<ServerEndpoint> serverEndpointsList =
      asList(
          SUCCESS,
          REDIRECT,
          ERROR,
          EXCEPTION,
          NOT_FOUND,
          CAPTURE_HEADERS,
          CAPTURE_PARAMETERS,
          QUERY_PARAM,
          PATH_PARAM,
          AUTH_REQUIRED,
          LOGIN,
          AUTH_ERROR,
          INDEXED_CHILD);

  @Override
  public Tomcat setupServer() throws Exception {
    Tomcat tomcatServer = new Tomcat();
    File baseDir = Files.createTempDirectory("tomcat").toFile();
    baseDir.deleteOnExit();
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());
    tomcatServer.setPort(port);
    tomcatServer.getConnector();

    Context servletContext =
        tomcatServer.addContext(getContextPath(), new File(".").getAbsolutePath());

    Tomcat.addServlet(servletContext, "testServlet", new TestServlet());

    // Mapping servlet to /* will result in all requests have a name of just a context.
    serverEndpointsList.stream()
        .filter(endpoint -> !endpoint.equals(NOT_FOUND))
        .forEach(
            endpoint -> servletContext.addServletMappingDecoded(endpoint.getPath(), "testServlet"));

    StandardHost host = (StandardHost) tomcatServer.getHost();
    host.setErrorReportValveClass(ErrorHandlerValve.class.getName());

    tomcatServer.start();
    return tomcatServer;
  }

  @Override
  public void stopServer(Tomcat server) throws LifecycleException {
    server.stop();
    server.destroy();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setContextPath("/app");
    options.setHasResponseCustomizer(serverEndpoint -> true);
    options.setTestCaptureRequestParameters(true);
    options.setTestErrorBody(false);

    options.setHasResponseSpan(
        endpoint -> endpoint == REDIRECT || endpoint == ERROR || endpoint == NOT_FOUND);

    options.setExpectedHttpRoute(
        (ServerEndpoint endpoint, String method) -> {
          if (method.equals(HttpConstants._OTHER)) {
            return getContextPath() + endpoint.getPath();
          }
          return super.expectedHttpRoute(endpoint, method);
        });
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String methodName;
    if (endpoint == REDIRECT) {
      methodName = "sendRedirect";
    } else if (endpoint == NOT_FOUND || endpoint == ERROR) {
      methodName = "sendError";
    } else {
      throw new AssertionError("Unexpected endpoint: " + endpoint.name());
    }
    span.hasKind(SpanKind.INTERNAL)
        .satisfies(spanData -> assertThat(spanData.getName()).endsWith("." + methodName))
        .hasAttributesSatisfyingExactly(
            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName),
            satisfies(CodeIncubatingAttributes.CODE_NAMESPACE, AbstractStringAssert::isNotEmpty));
    return span;
  }
}
