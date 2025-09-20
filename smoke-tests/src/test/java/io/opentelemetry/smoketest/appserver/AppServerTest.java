/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_TYPE;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.LINUX;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.WINDOWS;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import io.opentelemetry.smoketest.AppServer;
import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import io.opentelemetry.smoketest.TestContainerManager;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AppServerTest {
  private static final String TELEMETRY_DISTRO_VERSION = "telemetry.distro.version";

  protected boolean isWindows;

  @BeforeEach
  void setUp() {
    var appServer = getClass().getAnnotation(AppServer.class);
    if (appServer == null) {
      throw new IllegalStateException(
          "Server not specified, either add @AppServer annotation or override getAppServer method");
    }

    var jdk = appServer.jdk();
    isWindows = TestContainerManager.useWindowsContainers();

    // ibm-semeru-runtimes doesn't publish windows images
    // adoptopenjdk is deprecated and doesn't publish Windows 2022 images
    assumeFalse(isWindows && jdk.endsWith("-openj9"));

    testing().start(jdk, appServer.version(), isWindows);
  }

  protected abstract SmokeTestInstrumentationExtension testing();

  protected static SmokeTestInstrumentationExtension.Builder builder(String targetImagePrefix) {
    return SmokeTestInstrumentationExtension.builder(
        (jdk, serverVersion, windows) -> {
          String platformSuffix = windows ? "-windows" : "";
          String extraTag = "-20241014.11321808438";
          String fullSuffix = serverVersion + "-jdk" + jdk + platformSuffix + extraTag;
          return targetImagePrefix + ":" + fullSuffix;
        });
  }

  protected boolean testSmoke() {
    return true;
  }

  protected boolean testAsyncSmoke() {
    return true;
  }

  protected boolean testException() {
    return true;
  }

  protected boolean testRequestWebInfWebXml() {
    return true;
  }

  protected boolean testRequestOutsideDeployedApp() {
    return true;
  }

  protected boolean testJsp() {
    return true;
  }

  @Test
  void smokeTest() {
    assumeTrue(testSmoke());

    var response =
        testing()
            .client()
            .execute(RequestHeaders.of(HttpMethod.GET, "/app/greeting", "X-Test-Request", "test"))
            .aggregate()
            .join();
    String responseBody = response.contentUtf8();
    String version = testing().getAgentVersion();
    String expectedOsType = isWindows ? WINDOWS : LINUX;

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(getSpanName("/app/greeting"))
                            .hasKind(SpanKind.SERVER)
                            .hasResourceSatisfying(
                                resource -> resource.hasAttribute(OS_TYPE, expectedOsType))
                            .hasAttribute(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1")
                            .hasAttribute(
                                TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION, version)
                            .hasAttribute(UrlAttributes.URL_PATH, "/app/greeting")
                            .hasAttribute(
                                UrlAttributes.URL_FULL, "http://localhost:8080/app/greeting"),
                    span ->
                        span.hasName("GET /app/headers")
                            .hasKind(SpanKind.CLIENT)
                            .hasResourceSatisfying(
                                resource -> resource.hasAttribute(OS_TYPE, expectedOsType))
                            .hasAttribute(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1")
                            .hasAttribute(
                                TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION, version)
                            .hasAttribute(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1")
                            .hasAttribute(
                                io.opentelemetry.api.common.AttributeKey.stringArrayKey(
                                    "http.request.header.x-test-request"),
                                List.of("test")),
                    span ->
                        span.hasName(getSpanName("/app/headers"))
                            .hasKind(SpanKind.SERVER)
                            .hasResourceSatisfying(
                                resource -> resource.hasAttribute(OS_TYPE, expectedOsType))
                            .hasAttribute(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1")
                            .hasAttribute(
                                TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION, version)
                            .hasAttribute(UrlAttributes.URL_PATH, "/app/headers")
                            .hasAttribute(
                                UrlAttributes.URL_FULL, "http://localhost:8080/app/headers")));

    // trace id is present in the HTTP headers as reported by the called endpoint
    assertThat(responseBody).contains(testing().getSpanTraceIds().iterator().next());
  }

  @Test
  void testStaticFileFound() {
    var response = testing().client().get("/app/hello.txt").aggregate().join();
    assertThat(response.contentUtf8()).contains("Hello");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName(getSpanName("/app/hello.txt")).hasKind(SpanKind.SERVER)));
  }

  @Test
  void testStaticFileNotFound() {
    var response = testing().client().get("/app/file-that-does-not-exist").aggregate().join();

    assertThat(response.status().code()).isEqualTo(404);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(getSpanName("/app/file-that-does-not-exist"))
                            .hasKind(SpanKind.SERVER)
                            .hasAttribute(
                                UrlAttributes.URL_PATH, "/app/file-that-does-not-exist")));
  }

  @Test
  void testRequestForWebInfWebXml() {
    assumeTrue(testRequestWebInfWebXml());

    var response = testing().client().get("/app/WEB-INF/web.xml").aggregate().join();
    List<SpanData> traces = testing().spans();
    Set<String> traceIds = testing().getSpanTraceIds();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(getSpanName("/app/WEB-INF/web.xml"))
                            .hasKind(SpanKind.SERVER)
                            .hasAttribute(UrlAttributes.URL_PATH, "/app/WEB-INF/web.xml")));
  }

  @Test
  void testRequestWithError() {
    assumeTrue(testException());

    var response = testing().client().get("/app/exception").aggregate().join();
    List<SpanData> traces = testing().spans();
    Set<String> traceIds = testing().getSpanTraceIds();

    // There is one trace
    assertThat(traceIds).hasSize(1);

    // Response code is 500
    assertThat(response.status().code()).isEqualTo(500);

    // There is one server span
    long serverSpanCount =
        traces.stream()
            .filter(span -> span.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER)
            .count();
    assertThat(serverSpanCount).isOne();

    // Expected span names
    long spanNameCount =
        traces.stream()
            .filter(span -> getSpanName("/app/exception").equals(span.getName()))
            .count();
    assertThat(spanNameCount).isOne();

    // There is one exception
    long exceptionCount =
        traces.stream()
            .flatMap(span -> span.getEvents().stream())
            .filter(
                event ->
                    event
                            .getAttributes()
                            .get(
                                io.opentelemetry.api.common.AttributeKey.stringKey(
                                    "exception.message"))
                        != null)
            .filter(
                event ->
                    "This is expected"
                        .equals(
                            event
                                .getAttributes()
                                .get(
                                    io.opentelemetry.api.common.AttributeKey.stringKey(
                                        "exception.message"))))
            .count();
    assertThat(exceptionCount).isOne();

    // The span for the initial web request
    long urlPathCount =
        traces.stream()
            .filter(
                span -> "/app/exception".equals(span.getAttributes().get(UrlAttributes.URL_PATH)))
            .count();
    assertThat(urlPathCount).isOne();

    // Number of spans tagged with current otel library version
    long versionCount =
        traces.stream()
            .filter(
                span ->
                    testing()
                        .getAgentVersion()
                        .equals(
                            span.getResource()
                                .getAttributes()
                                .get(
                                    io.opentelemetry.api.common.AttributeKey.stringKey(
                                        TELEMETRY_DISTRO_VERSION))))
            .count();
    assertThat(versionCount).isEqualTo(traces.size());

    // Number of spans tagged with expected OS type
    String expectedOsType = isWindows ? WINDOWS : LINUX;
    long osTypeCount =
        traces.stream()
            .filter(span -> expectedOsType.equals(span.getResource().getAttributes().get(OS_TYPE)))
            .count();
    assertThat(osTypeCount).isEqualTo(traces.size());
  }

  @Test
  void testRequestOutsideDeployedApplication() {
    assumeTrue(testRequestOutsideDeployedApp());

    var response =
        testing()
            .client()
            .get("/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless")
            .aggregate()
            .join();
    List<SpanData> traces = testing().spans();
    Set<String> traceIds = testing().getSpanTraceIds();

    // There is one trace
    assertThat(traceIds).hasSize(1);

    // Response code is 404
    assertThat(response.status().code()).isEqualTo(404);

    // There is one server span
    long serverSpanCount =
        traces.stream()
            .filter(span -> span.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER)
            .count();
    assertThat(serverSpanCount).isOne();

    // Expected span names
    long spanNameCount =
        traces.stream()
            .filter(
                span ->
                    getSpanName(
                            "/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless")
                        .equals(span.getName()))
            .count();
    assertThat(spanNameCount).isOne();

    // The span for the initial web request
    long urlPathCount =
        traces.stream()
            .filter(
                span ->
                    "/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless"
                        .equals(span.getAttributes().get(UrlAttributes.URL_PATH)))
            .count();
    assertThat(urlPathCount).isOne();

    // Number of spans with http protocol version
    long protocolVersionCount =
        traces.stream()
            .filter(
                span ->
                    "1.1"
                        .equals(
                            span.getAttributes().get(NetworkAttributes.NETWORK_PROTOCOL_VERSION)))
            .count();
    assertThat(protocolVersionCount).isOne();

    // Number of spans tagged with current otel library version
    long versionCount =
        traces.stream()
            .filter(
                span ->
                    testing()
                        .getAgentVersion()
                        .equals(
                            span.getResource()
                                .getAttributes()
                                .get(
                                    io.opentelemetry.api.common.AttributeKey.stringKey(
                                        TELEMETRY_DISTRO_VERSION))))
            .count();
    assertThat(versionCount).isEqualTo(traces.size());

    // Number of spans tagged with expected OS type
    String expectedOsType = isWindows ? WINDOWS : LINUX;
    long osTypeCount =
        traces.stream()
            .filter(span -> expectedOsType.equals(span.getResource().getAttributes().get(OS_TYPE)))
            .count();
    assertThat(osTypeCount).isEqualTo(traces.size());
  }

  @Test
  void asyncSmokeTest() {
    assumeTrue(testAsyncSmoke());

    var response = testing().client().get("/app/asyncgreeting").aggregate().join();
    List<SpanData> traces = testing().spans();
    Set<String> traceIds = testing().getSpanTraceIds();
    String responseBody = response.contentUtf8();

    // There is one trace
    assertThat(traceIds).hasSize(1);

    // trace id is present in the HTTP headers as reported by the called endpoint
    assertThat(responseBody).contains(traceIds.iterator().next());

    // Server spans in the distributed trace
    long serverSpanCount =
        traces.stream()
            .filter(span -> span.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER)
            .count();
    assertThat(serverSpanCount).isEqualTo(2);

    // Expected span names
    long asyncGreetingSpanCount =
        traces.stream()
            .filter(span -> getSpanName("/app/asyncgreeting").equals(span.getName()))
            .count();
    assertThat(asyncGreetingSpanCount).isOne();

    long headersSpanCount =
        traces.stream().filter(span -> getSpanName("/app/headers").equals(span.getName())).count();
    assertThat(headersSpanCount).isOne();

    // The span for the initial web request
    long urlPathCount =
        traces.stream()
            .filter(
                span ->
                    "/app/asyncgreeting".equals(span.getAttributes().get(UrlAttributes.URL_PATH)))
            .count();
    assertThat(urlPathCount).isOne();

    // Client span for the remote call
    long urlFullCount =
        traces.stream()
            .filter(
                span ->
                    "http://localhost:8080/app/headers"
                        .equals(span.getAttributes().get(UrlAttributes.URL_FULL)))
            .count();
    assertThat(urlFullCount).isOne();

    // Server span for the remote call
    long headersUrlPathCount =
        traces.stream()
            .filter(span -> "/app/headers".equals(span.getAttributes().get(UrlAttributes.URL_PATH)))
            .count();
    assertThat(headersUrlPathCount).isOne();

    // Number of spans with http protocol version
    long protocolVersionCount =
        traces.stream()
            .filter(
                span ->
                    "1.1"
                        .equals(
                            span.getAttributes().get(NetworkAttributes.NETWORK_PROTOCOL_VERSION)))
            .count();
    assertThat(protocolVersionCount).isEqualTo(3);

    // Number of spans tagged with current otel library version
    long versionCount =
        traces.stream()
            .filter(
                span ->
                    testing()
                        .getAgentVersion()
                        .equals(
                            span.getResource()
                                .getAttributes()
                                .get(
                                    io.opentelemetry.api.common.AttributeKey.stringKey(
                                        TELEMETRY_DISTRO_VERSION))))
            .count();
    assertThat(versionCount).isEqualTo(3);

    // Number of spans tagged with expected OS type
    String expectedOsType = isWindows ? WINDOWS : LINUX;
    long osTypeCount =
        traces.stream()
            .filter(span -> expectedOsType.equals(span.getResource().getAttributes().get(OS_TYPE)))
            .count();
    assertThat(osTypeCount).isEqualTo(3);
  }

  //  @Test
  //  void jspSmokeTestForSnippetInjection(String appServer, String jdk) throws Exception {
  //    assumeTrue(testJsp());
  //
  //    var response = client().get("/app/jsp").aggregate().join();
  //    List<SpanData> traces = waitForTraces();
  //    String responseBody = response.contentUtf8();
  //
  //    assertThat(response.status().isSuccess()).isTrue();
  //    assertThat(responseBody).contains("Successful JSP test");
  //    assertThat(responseBody).contains("<script>console.log(hi)</script>");
  //
  //    if (expectServerSpan()) {
  //      long serverSpanCount =
  //          traces.stream()
  //              .filter(span -> span.getKind() == io.opentelemetry.api.trace.SpanKind.SERVER)
  //              .count();
  //      assertThat(serverSpanCount).isOne();
  //
  //      long jspSpanCount =
  //          traces.stream().filter(span -> "GET /app/jsp".equals(span.getName())).count();
  //      assertThat(jspSpanCount).isOne();
  //    }
  //  }

  protected boolean expectServerSpan() {
    return true;
  }

  protected String getSpanName(String path) {
    switch (path) {
      case "/app/greeting":
      case "/app/headers":
      case "/app/exception":
      case "/app/asyncgreeting":
        return "GET " + path;
      case "/app/hello.txt":
      case "/app/file-that-does-not-exist":
        return "GET /app/*";
      default:
        return "GET";
    }
  }
}
