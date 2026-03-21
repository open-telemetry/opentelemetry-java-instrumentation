/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OS_TYPE;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.LINUX;
import static io.opentelemetry.semconv.incubating.OsIncubatingAttributes.OsTypeIncubatingValues.WINDOWS;
import static io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.smoketest.AbstractSmokeTest;
import io.opentelemetry.smoketest.TestContainerManager;
import io.opentelemetry.smoketest.TestImageVersions;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AppServerTest extends AbstractSmokeTest<AppServerImage> {
  protected boolean isWindows;
  protected String serverVersion;

  @BeforeAll
  void setUpServer() {
    var appServer = getClass().getAnnotation(AppServer.class);
    if (appServer == null) {
      throw new IllegalStateException("Server not specified, add @AppServer annotation");
    }

    var jdk = appServer.jdk();
    isWindows = TestContainerManager.useWindowsContainers();

    // In reduced mode (PR builds), only run a representative subset of the full matrix.
    // The full matrix runs on merge to main.
    if (Boolean.getBoolean("reducedSmokeTests")) {
      skipIfNotReduced();
    }

    // ibm-semeru-runtimes doesn't publish windows images
    // adoptopenjdk is deprecated and doesn't publish Windows 2022 images
    assumeFalse(isWindows && jdk.endsWith("-openj9"));

    serverVersion = appServer.version();
    startWithoutCleanup(new AppServerImage(jdk, serverVersion, isWindows));
  }

  // Reduced smoke test rules for PR builds. The full matrix runs on merge to main.
  // These rules match reduceTargets() in smoke-tests/images/servlet/build.gradle.kts,
  // which controls which Docker images are built. Both sides apply the same logic:
  // Tomcat covers all JDKs (hotspot), TomEE covers all JDKs (openj9),
  // Websphere is already minimal, other servers test only minimum JDK on hotspot.
  private void skipIfNotReduced() {
    var appServer = getClass().getAnnotation(AppServer.class);
    String jdk = appServer.jdk();
    boolean isOpenj9 = jdk.contains("-openj9");

    if (this instanceof TomcatSmokeTest) {
      assumeFalse(isOpenj9, "Reduced mode: Tomcat runs hotspot only");
      return;
    }
    if (this instanceof TomeeSmokeTest) {
      assumeTrue(isOpenj9, "Reduced mode: TomEE runs openj9 only");
      return;
    }
    if (this instanceof WebsphereSmokeTest) {
      return;
    }
    // All other servers: hotspot only, minimum JDK per server version
    assumeFalse(isOpenj9, "Reduced mode: hotspot only");
    assumeTrue(isMinimumJdk(appServer), "Reduced mode: only minimum JDK per server version");
  }

  private boolean isMinimumJdk(AppServer appServer) {
    int myJdk = parseJdkVersion(appServer.jdk());
    for (Class<?> sibling : getClass().getEnclosingClass().getDeclaredClasses()) {
      AppServer other = sibling.getAnnotation(AppServer.class);
      if (other != null
          && other.version().equals(appServer.version())
          && !other.jdk().contains("-openj9")
          && parseJdkVersion(other.jdk()) < myJdk) {
        return false;
      }
    }
    return true;
  }

  private static int parseJdkVersion(String jdk) {
    return Integer.parseInt(jdk.split("-")[0]);
  }

  @AfterAll
  void afterAll() {
    stop();
  }

  static Function<AppServerImage, String> appServerImage(String targetImagePrefix) {
    return a -> {
      String platformSuffix = a.isWindows() ? "-windows" : "";
      String extraTag = "-" + TestImageVersions.SERVLET_VERSION;
      String fullSuffix = a.getServerVersion() + "-jdk" + a.getJdk() + platformSuffix + extraTag;
      return targetImagePrefix + ":" + fullSuffix;
    };
  }

  protected boolean testRequestOutsideDeployedApp() {
    return true;
  }

  protected boolean testJsp() {
    return true;
  }

  @Test
  void smokeTest() {
    String path = "/app/greeting";
    var response =
        client()
            .execute(RequestHeaders.of(HttpMethod.GET, path, "X-Test-Request", "test"))
            .aggregate()
            .join();

    assertFullTrace(path, response.contentUtf8(), true);
  }

  private void assertFullTrace(String path, String responseBody, boolean captureHeader) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> {
                  assertServerSpan(span, path);
                  if (captureHeader) {
                    span.hasAttribute(
                        stringArrayKey("http.request.header.x-test-request"), List.of("test"));
                  }
                },
                span ->
                    assertSpan(span)
                        .hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttribute(URL_FULL, "http://localhost:8080/app/headers"),
                span ->
                    assertServerSpan(span, "/app/headers")
                        .hasAttribute(CLIENT_ADDRESS, "127.0.0.1")));

    // trace id is present in the HTTP headers as reported by the called endpoint
    assertThat(responseBody).contains(getSpanTraceIds().iterator().next());
  }

  private String getExpectedOsType() {
    return isWindows ? WINDOWS : LINUX;
  }

  @Test
  void testStaticFileFound() {
    String path = "/app/hello.txt";
    var response = client().get(path).aggregate().join();
    assertThat(response.contentUtf8()).contains("Hello");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> assertServerSpan(span, path)));
  }

  @Test
  void testStaticFileNotFound() {
    String path = "/app/file-that-does-not-exist";
    var response = client().get(path).aggregate().join();

    assertThat(response.status().code()).isEqualTo(404);

    getAndAssertServerSpan(span -> assertServerSpan(span, path));
  }

  @Test
  void testRequestForWebInfWebXml() {
    String path = "/app/WEB-INF/web.xml";
    AggregatedHttpResponse response = client().get(path).aggregate().join();

    assertThat(response.status().code()).isEqualTo(404);

    getAndAssertServerSpan(span -> assertServerSpan(span, path));
  }

  @Test
  void testRequestWithError() {
    String path = "/app/exception";
    var response = client().get(path).aggregate().join();

    assertThat(response.status().code()).isEqualTo(500);

    getAndAssertServerSpan(
        span ->
            assertServerSpan(span, path)
                .hasEventsSatisfyingExactly(
                    event ->
                        event.hasAttributesSatisfying(
                            equalTo(stringKey("exception.message"), "This is expected"))));
  }

  @Test
  void testRequestOutsideDeployedApplication() {
    assumeTrue(testRequestOutsideDeployedApp());

    String path = "/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless";
    var response = client().get(path).aggregate().join();
    assertThat(response.status().code()).isEqualTo(404);

    getAndAssertServerSpan(span -> assertServerSpan(span, path));
  }

  @Test
  void asyncSmokeTest() {
    String path = "/app/asyncgreeting";
    var response = client().get(path).aggregate().join();
    assertFullTrace(path, response.contentUtf8(), false);
  }

  @Test
  void jspSmokeTestForSnippetInjection() {
    assumeTrue(testJsp());

    var response = client().get("/app/jsp").aggregate().join();

    assertThat(response.contentUtf8())
        .contains("Successful JSP test")
        .contains("<script>console.log(hi)</script>");
    assertThat(response.status().isSuccess()).isTrue();

    if (expectServerSpan()) {
      getAndAssertServerSpan(span -> span.hasName("GET /app/jsp"));
    }
  }

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

  private SpanDataAssert assertSpan(SpanDataAssert span) {
    return span.hasResourceSatisfying(
            resource ->
                resource
                    .hasAttribute(OS_TYPE, getExpectedOsType())
                    .hasAttribute(TELEMETRY_DISTRO_VERSION, getAgentVersion()))
        .hasAttribute(NETWORK_PROTOCOL_VERSION, "1.1");
  }

  private SpanDataAssert assertServerSpan(SpanDataAssert span, String path) {
    return assertSpan(span)
        .hasName(getSpanName(path))
        .hasKind(SpanKind.SERVER)
        .hasAttribute(URL_PATH, path);
  }

  private static void getAndAssertServerSpan(Consumer<SpanDataAssert> assertion) {
    Optional<SpanData> serverSpan =
        testing.spans().stream().filter(span -> span.getKind() == SpanKind.SERVER).findFirst();

    assertThat(serverSpan).hasValueSatisfying(span -> assertion.accept(assertThat(span)));
  }
}
