/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.AbstractServlet3Test;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import spock.lang.Unroll;

@Unroll
public abstract class TomcatServlet3Test extends AbstractServlet3Test<Tomcat, Context> {

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
  private static final TestAccessLogValve accessLogValue = new TestAccessLogValve();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setExpectedException(new ServletException(EXCEPTION.getBody()));
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
      span.satisfies(s -> assertThat(s.getName()).matches("\\.sendError$"))
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
    servletContext
        .getJarScanner()
        .setJarScanFilter(
            new JarScanFilter() {
              @Override
              public boolean check(JarScanType jarScanType, String jarName) {
                return false;
              }
            });

    //    setupAuthentication(tomcatServer, servletContext)
    setupServlets(servletContext);

    ((StandardHost) tomcatServer.getHost())
        .setErrorReportValveClass(ErrorHandlerValve.class.getName());
    tomcatServer.getHost().getPipeline().addValve(accessLogValue);

    tomcatServer.start();

    return tomcatServer;
  }

  public void setup() {
    accessLogValue.getLoggedIds().clear();
  }

  @Override
  public void stopServer(Tomcat server) {
    try {
      server.stop();
      server.destroy();
    } catch (LifecycleException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("ClassNewInstance")
  @Override
  public void addServlet(Context servletContext, String path, Class<? extends Servlet> servlet) {
    String name = UUID.randomUUID().toString();
    try {
      Tomcat.addServlet(servletContext, name, servlet.newInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    servletContext.addServletMappingDecoded(path, name);
  }

  @ParameterizedTest
  @CsvSource({"1", "4"})
  void access_log_has_ids_for__count_requests(int count) {
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

    IntStream.range(0, count)
        .forEach(
            i -> {
              testing.waitAndAssertTraces(
                  trace ->
                      trace.hasSpansSatisfyingExactly(
                          span ->
                              assertServerSpan(
                                  span, "GET", ACCESS_LOG_SUCCESS, SUCCESS.getStatus()),
                          span -> assertControllerSpan(span, null)));

              List<List<SpanData>> traces = TelemetryDataUtil.groupTraces(testing.spans());
              assertThat(loggedTraces).contains(traces.get(i).get(0).getTraceId());
              assertThat(loggedSpans).contains(traces.get(i).get(0).getSpanId());
            });
  }

  @Test
  void access_log_has_ids_for_error_request() {
    Assumptions.assumeTrue(testError());

    AggregatedHttpRequest request = request(ACCESS_LOG_ERROR, "GET");
    AggregatedHttpResponse response = client.execute(request).aggregate().join();

    assertThat(response.status().code()).isEqualTo(ACCESS_LOG_ERROR.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(ACCESS_LOG_ERROR.getBody());

    int spanCount = 2;
    if (errorEndpointUsesSendError()) {
      spanCount++;
    }

    List<SpanData> spanData = TelemetryDataUtil.groupTraces(testing.spans()).get(0);
    List<SpanDataAssert> spans =
        spanData.stream().map(OpenTelemetryAssertions::assertThat).collect(Collectors.toList());
    assertThat(spans).hasSize(spanCount);

    assertServerSpan(spans.get(0), "GET", ACCESS_LOG_ERROR, ERROR.getStatus());
    assertControllerSpan(spans.get(1), null);
    if (errorEndpointUsesSendError()) {
      spans
          .get(2)
          .satisfies(s -> assertThat(s.getName()).matches("\\.sendError$"))
          .hasKind(SpanKind.INTERNAL)
          .hasParent(spanData.get(1));
    }

    accessLogValue.waitForLoggedIds(1);
    Map.Entry<String, String> entry = accessLogValue.getLoggedIds().get(0);

    assertThat(spanData.get(0).getTraceId()).isEqualTo(entry.getKey());
    assertThat(spanData.get(0).getSpanId()).isEqualTo(entry.getValue());
  }
}
