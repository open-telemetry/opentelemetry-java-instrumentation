/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
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
import java.util.UUID;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.extension.RegisterExtension;

class TomcatAsyncTest extends AbstractHttpServerTest<Tomcat> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  public Tomcat setupServer() throws Exception {
    Tomcat tomcatServer = new Tomcat();
    File baseDir = Files.createTempDirectory("tomcat").toFile();
    baseDir.deleteOnExit();
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());
    tomcatServer.setPort(port);
    tomcatServer.getConnector().setEnableLookups(true); // get localhost instead of 127.0.0.1

    File applicationDir = new File(baseDir, "/webapps/ROOT");
    if (!applicationDir.exists()) {
      applicationDir.mkdirs();
      applicationDir.deleteOnExit();
    }

    Context servletContext =
        tomcatServer.addWebapp(getContextPath(), applicationDir.getAbsolutePath());
    // Speed up startup by disabling jar scanning:
    servletContext.getJarScanner().setJarScanFilter((jarScanType, jarName) -> false);

    setupServlets(servletContext);
    tomcatServer.start();
    return tomcatServer;
  }

  protected void setupServlets(Context context) throws Exception {
    Class<AsyncServlet> servlet = AsyncServlet.class;

    addServlet(context, SUCCESS.getPath(), servlet);
    addServlet(context, QUERY_PARAM.getPath(), servlet);
    addServlet(context, ERROR.getPath(), servlet);
    addServlet(context, EXCEPTION.getPath(), servlet);
    addServlet(context, REDIRECT.getPath(), servlet);
    addServlet(context, AUTH_REQUIRED.getPath(), servlet);
    addServlet(context, CAPTURE_HEADERS.getPath(), servlet);
    addServlet(context, INDEXED_CHILD.getPath(), servlet);
  }

  void addServlet(Context servletContext, String path, Class<AsyncServlet> servlet)
      throws Exception {
    String name = UUID.randomUUID().toString();
    Tomcat.addServlet(servletContext, name, servlet.getDeclaredConstructor().newInstance());
    servletContext.addServletMappingDecoded(path, name);
  }

  @Override
  public void stopServer(Tomcat server) throws LifecycleException {
    server.stop();
    server.destroy();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setContextPath("/tomcat-context");

    options.setExpectedHttpRoute(
        (ServerEndpoint endpoint, String method) -> {
          if (method.equals(HttpConstants._OTHER)) {
            return getContextPath() + endpoint.getPath();
          }
          if (endpoint.equals(NOT_FOUND)) {
            return getContextPath() + "/*";
          }
          return super.expectedHttpRoute(endpoint, method);
        });

    options.setHasResponseSpan(endpoint -> endpoint == NOT_FOUND || endpoint == REDIRECT);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String methodName;
    if (endpoint == REDIRECT) {
      methodName = "sendRedirect";
    } else if (endpoint == NOT_FOUND) {
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
