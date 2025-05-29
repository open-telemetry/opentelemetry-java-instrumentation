/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.AbstractServlet5Test;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import jakarta.servlet.Servlet;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public abstract class TomcatServlet5Test extends AbstractServlet5Test<Tomcat, Context> {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private static final ServerEndpoint ACCESS_LOG_SUCCESS =
      new ServerEndpoint(
          "ACCESS_LOG_SUCCESS",
          "success?access-log=true",
          SUCCESS.getStatus(),
          SUCCESS.getBody(),
          false);
  private static final ServerEndpoint ACCESS_LOG_ERROR =
      new ServerEndpoint(
          "ACCESS_LOG_ERROR",
          "error-status?access-log=true",
          ERROR.getStatus(),
          ERROR.getBody(),
          false);
  private final TestAccessLogValve accessLogValue = new TestAccessLogValve();

  @TempDir private static File tempDir;

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setContextPath("/tomcat-context");
    options.setTestError(testError());
  }

  public boolean testError() {
    return false;
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, SpanData parentSpan, String method, ServerEndpoint endpoint) {
    if (NOT_FOUND.equals(endpoint)) {
      span.satisfies(s -> assertThat(s.getName()).matches(".*\\.sendError"))
          .hasKind(SpanKind.INTERNAL)
          .hasParent(parentSpan);
    }
    return super.assertResponseSpan(span, parentSpan, method, endpoint);
  }

  @Override
  protected boolean hasResponseSpan(ServerEndpoint endpoint) {
    return endpoint == NOT_FOUND || super.hasResponseSpan(endpoint);
  }

  @Override
  protected Tomcat setupServer() throws Exception {
    Tomcat tomcatServer = new Tomcat();

    File baseDir = tempDir;
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());

    tomcatServer.setPort(port);
    tomcatServer.getConnector().setEnableLookups(true); // get localhost instead of 127.0.0.1

    File applicationDir = new File(baseDir, "/webapps/ROOT");
    applicationDir.mkdirs();

    Context servletContext =
        tomcatServer.addWebapp(getContextPath(), applicationDir.getAbsolutePath());
    // Speed up startup by disabling jar scanning:
    servletContext.getJarScanner().setJarScanFilter((jarScanType, jarName) -> false);

    setupServlets(servletContext);

    ((StandardHost) tomcatServer.getHost())
        .setErrorReportValveClass(ErrorHandlerValve.class.getName());
    tomcatServer.getHost().getPipeline().addValve(accessLogValue);

    tomcatServer.start();

    return tomcatServer;
  }

  @BeforeEach
  void setUp() {
    accessLogValue.getLoggedIds().clear();
    testing().clearAllExportedData();
  }

  @Override
  public void stopServer(Tomcat server) throws LifecycleException {
    server.stop();
    server.destroy();
  }

  @Override
  public void addServlet(Context servletContext, String path, Class<? extends Servlet> servlet)
      throws Exception {
    String name = UUID.randomUUID().toString();
    Tomcat.addServlet(servletContext, name, servlet.getConstructor().newInstance());
    servletContext.addServletMappingDecoded(path, name);
  }

  @ParameterizedTest
  @CsvSource({"1", "4"})
  void accessLogHasIdsForCountRequests(int count) {
    AggregatedHttpRequest request = request(ACCESS_LOG_SUCCESS, "GET");

    IntStream.range(0, count)
        .mapToObj(i -> client.execute(request).aggregate().join())
        .forEach(
            response -> {
              assertThat(response.status().code()).isEqualTo(ACCESS_LOG_SUCCESS.getStatus());
              assertThat(response.contentUtf8()).isEqualTo(ACCESS_LOG_SUCCESS.getBody());
            });

    accessLogValue.waitForLoggedIds(count);
    assertThat(accessLogValue.getLoggedIds().size()).isEqualTo(count);
    List<String> loggedTraces =
        accessLogValue.getLoggedIds().stream().map(Map.Entry::getKey).collect(Collectors.toList());
    List<String> loggedSpans =
        accessLogValue.getLoggedIds().stream()
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

    testing()
        .waitAndAssertTraces(
            IntStream.range(0, count)
                .mapToObj(
                    i ->
                        (Consumer<TraceAssert>)
                            trace -> {
                              trace.hasSpansSatisfyingExactly(
                                  span ->
                                      assertServerSpan(
                                          span, "GET", ACCESS_LOG_SUCCESS, SUCCESS.getStatus()),
                                  span -> assertControllerSpan(span, null));
                              SpanData span = trace.getSpan(0);
                              assertThat(loggedTraces).contains(span.getTraceId());
                              assertThat(loggedSpans).contains(span.getSpanId());
                            })
                .collect(Collectors.toList()));
  }

  @Test
  void accessLogHasIdsForErrorRequest() {
    Assumptions.assumeTrue(testError());

    AggregatedHttpRequest request = request(ACCESS_LOG_ERROR, "GET");
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(ACCESS_LOG_ERROR.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(ACCESS_LOG_ERROR.getBody());

    List<BiConsumer<SpanDataAssert, TraceAssert>> spanDataAsserts = new ArrayList<>();
    spanDataAsserts.add(
        (span, trace) -> assertServerSpan(span, "GET", ACCESS_LOG_ERROR, ERROR.getStatus()));
    spanDataAsserts.add((span, trace) -> assertControllerSpan(span, null));
    if (errorEndpointUsesSendError()) {
      spanDataAsserts.add(
          (span, trace) ->
              span.satisfies(s -> assertThat(s.getName()).matches(".*\\.sendError"))
                  .hasKind(SpanKind.INTERNAL)
                  .hasParent(trace.getSpan(1)));
    }

    accessLogValue.waitForLoggedIds(1);
    testing()
        .waitAndAssertTraces(
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  spanDataAsserts.stream()
                      .map(e -> (Consumer<SpanDataAssert>) span -> e.accept(span, trace))
                      .collect(Collectors.toList()));
              SpanData span = trace.getSpan(0);
              Map.Entry<String, String> entry = accessLogValue.getLoggedIds().get(0);
              assertThat(entry.getKey()).isEqualTo(span.getTraceId());
              assertThat(entry.getValue()).isEqualTo(span.getSpanId());
            });
  }
}
