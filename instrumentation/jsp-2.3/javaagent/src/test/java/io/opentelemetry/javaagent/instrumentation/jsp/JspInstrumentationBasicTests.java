/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.javaagent.instrumentation.jsp.AbstractJspInstrumentationTest.assertServerSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import java.io.File;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.JasperException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

class JspInstrumentationBasicTests extends AbstractHttpServerUsingTest<Tomcat> {
  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  protected static String baseUrl = "";


  @Override
  protected Tomcat setupServer() throws Exception {
    File baseDir = Files.createTempDirectory("jsp").toFile();
    baseDir.deleteOnExit();

    Tomcat tomcatServer = new Tomcat();
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());
    tomcatServer.setPort(port);
    tomcatServer.getConnector();

    // comment to debug
    tomcatServer.setSilent(true);

    // this is needed in tomcat 9, this triggers the creation of a connector, will not
    // affect tomcat 7 and 8
    // https://stackoverflow.com/questions/48998387/code-works-with-embedded-apache-tomcat-8-but-not-with-9-whats-changed
    tomcatServer.getConnector();

    baseUrl = "http://localhost:" + port + "/" + getContextPath();
    client = WebClient.of(baseUrl);

    tomcatServer.addWebapp(
        "/" + getContextPath(),
        JspInstrumentationBasicTests.class.getResource("/webapps/jsptest").getPath());

    tomcatServer.start();
    System.out.println(
        "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + port + "/");
    return tomcatServer;
  }

  @Override
  protected void stopServer(Tomcat tomcat) throws Exception {
    tomcat.stop();
    tomcat.destroy();
  }

  @Override
  protected String getContextPath() {
    return "jsptest-context";
  }

  @BeforeAll
  protected void setUp() {
    startServer();
  }

  @AfterAll
  protected void cleanUp() {
    cleanupServer();
  }

  @ParameterizedTest(name = "GET {0}")
  @ArgumentsSource(NonErroneousArgs.class)
  void testNonErroneousGet(
      String testName, String jspFileName, String jspClassName, String jspClassNamePrefix) {
    AggregatedHttpResponse res = client.get(jspFileName).aggregate().join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertServerSpan(new AbstractJspInstrumentationTest.ServerSpanAssertionBuilder()
                        .withSpan(span)
                        .withMethod("GET")
                        .withRoute("/" + getContextPath() + jspFileName)
                        .withPort(port)
                        .withResponseStatus(200)
                        .build()),
                span ->
                    assertCompileSpan(
                        span,
                        trace.getSpan(0),
                        jspFileName,
                        jspClassNamePrefix + jspClassName,
                        null),
                span -> assertRenderSpan(span, trace.getSpan(0), jspFileName, null, null, null)));

    assertThat(res.status().code()).isEqualTo(200);
  }

  static class NonErroneousArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of("no java jsp", "/nojava.jsp", "nojava_jsp", ""),
          Arguments.of("basic loop jsp", "/common/loop.jsp", "loop_jsp", "common."),
          Arguments.of("invalid HTML markup", "/invalidMarkup.jsp", "invalidMarkup_jsp", ""));
    }
  }

  @Test
  void testNonErroneousGetWithQueryString() {
    String queryString = "HELLO";
    AggregatedHttpResponse res = client.get("/getQuery.jsp?" + queryString).aggregate().join();
    String route = "/" + getContextPath() + "/getQuery.jsp";

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + route)
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, route),
                            equalTo(UrlAttributes.URL_QUERY, queryString),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(
                                UserAgentAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(HttpAttributes.HTTP_ROUTE, route),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, port),
                            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class))),
                span ->
                    assertCompileSpan(
                        span, trace.getSpan(0), "/getQuery.jsp", "getQuery_jsp", null),
                span ->
                    assertRenderSpan(span, trace.getSpan(0), "/getQuery.jsp", null, null, null)));
    assertThat(res.status().code()).isEqualTo(200);
  }

  @Test
  void testNonErroneousPost() {
    RequestHeaders headers =
        RequestHeaders.builder(HttpMethod.POST, "/post.jsp")
            .contentType(MediaType.FORM_DATA)
            .build();

    AggregatedHttpResponse res = client.execute(headers, "name=world").aggregate().join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertServerSpan(
                        span, new ServerSpanAssertionBuilder()
                            .withMethod("POST")
                            .withRoute("/" + getContextPath() + "/post.jsp")
                            .withResponseStatus(200L)
                            .build()),
                span -> assertCompileSpan(span, trace.getSpan(0), "/post.jsp", "post_jsp", null),
                span -> assertRenderSpan(span, trace.getSpan(0), "/post.jsp", null, null, null)));
    assertThat(res.status().code()).isEqualTo(200);
  }

  @ParameterizedTest(name = "GET jsp with {0}")
  @ArgumentsSource(ErroneousRuntimeErrorsArgs.class)
  void testErroneousRuntimeErrorsGet(
      String testName, String jspFileName, String jspClassName, Class<?> exceptionClass) {
    AggregatedHttpResponse res = client.get(jspFileName).aggregate().join();
    String route = "/" + getContextPath() + jspFileName;

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertServerSpan(span, new ServerSpanAssertionBuilder()
                    .withMethod("GET")
                    .withRoute(route)
                    .withResponseStatus(500L)
                    .withExceptionClass(exceptionClass)
                    .build()),
                span -> assertCompileSpan(span, trace.getSpan(0), jspFileName, jspClassName, null),
                span ->
                    assertRenderSpan(
                        span, trace.getSpan(0), jspFileName, null, null, exceptionClass)));
    assertThat(res.status().code()).isEqualTo(500);
  }

  static class ErroneousRuntimeErrorsArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "java runtime error",
              "/runtimeError.jsp",
              "runtimeError_jsp",
              ArithmeticException.class),
          Arguments.of(
              "invalid write",
              "/invalidWrite.jsp",
              "invalidWrite_jsp",
              IndexOutOfBoundsException.class),
          Arguments.of(
              "invalid write", "/getQuery.jsp", "getQuery_jsp", NullPointerException.class));
    }
  }

  @Test
  void testNonErroneousIncludePlainHtmlGet() {
    AggregatedHttpResponse res = client.get("/includes/includeHtml.jsp").aggregate().join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertServerSpan(
                        span,
                        new ServerSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + "/includes/includeHtml.jsp")
                            .withResponseStatus(200L)
                            .build()),
                span ->
                    assertCompileSpan(
                        span,
                        trace.getSpan(0),
                        "/includes/includeHtml.jsp",
                        "includes.includeHtml_jsp",
                        null),
                span ->
                    assertRenderSpan(
                        span, trace.getSpan(0), "/includes/includeHtml.jsp", null, null, null)));
    assertThat(res.status().code()).isEqualTo(200);
  }

  @Test
  void testNonErroneousMultiGet() {
    AggregatedHttpResponse res = client.get("/includes/includeMulti.jsp").aggregate().join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertServerSpan(
                        span,
                        new ServerSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + "/includes/includeMulti.jsp")
                            .withResponseStatus(200L)
                            .build()),
                span ->
                    assertCompileSpan(
                        span,
                        trace.getSpan(0),
                        "/includes/includeMulti.jsp",
                        "includes.includeMulti_jsp",
                        null),
                span ->
                    assertRenderSpan(
                        span, trace.getSpan(0), "/includes/includeMulti.jsp", null, null, null),
                span ->
                    assertCompileSpan(
                        span,
                        trace.getSpan(2),
                        "/common/javaLoopH2.jsp",
                        "common.javaLoopH2_jsp",
                        null),
                span ->
                    assertRenderSpan(
                        span,
                        trace.getSpan(2),
                        "/common/javaLoopH2.jsp",
                        "/includes/includeMulti.jsp",
                        null,
                        null),
                span ->
                    assertCompileSpan(
                        span,
                        trace.getSpan(2),
                        "/common/javaLoopH2.jsp",
                        "common.javaLoopH2_jsp",
                        null),
                span ->
                    assertRenderSpan(
                        span,
                        trace.getSpan(2),
                        "/common/javaLoopH2.jsp",
                        "/includes/includeMulti.jsp",
                        null,
                        null)));
    assertThat(res.status().code()).isEqualTo(200);
  }

  @ParameterizedTest
  @ArgumentsSource(CompileErrorsArgs.class)
  void testCompileErrorShouldNotProduceRenderTracesAndSpans(
      String jspFileName, String jspClassName, String jspClassNamePrefix) {
    AggregatedHttpResponse res = client.get(jspFileName).aggregate().join();

    String route = "/" + getContextPath() + jspFileName;

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertServerSpan(span, new ServerSpanAssertionBuilder()
                    .withMethod("GET")
                    .withRoute(route)
                    .withResponseStatus(500L)
                    .withExceptionClass(JasperException.class)
                    .build()),
                span ->
                    span.hasName("Compile " + jspFileName)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE,
                                            JasperException.class.getCanonicalName()),
                                        satisfies(
                                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class)),
                                        satisfies(
                                            ExceptionAttributes.EXCEPTION_MESSAGE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp." + jspClassNamePrefix + jspClassName),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler"))));
    assertThat(res.status().code()).isEqualTo(500);
  }

  static class CompileErrorsArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of("/compileError.jsp", "compileError_jsp", ""),
          Arguments.of(
              "/forwards/forwardWithCompileError.jsp", "forwardWithCompileError_jsp", "forwards."));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/common/hello.html"})
  void testDirectStaticFileReference(String staticFile) {
    String route = "/" + getContextPath() + "/*";
    AggregatedHttpResponse res = client.get(staticFile).aggregate().join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + route)
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, "/" + getContextPath() + staticFile),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(
                                UserAgentAttributes.USER_AGENT_ORIGINAL,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(HttpAttributes.HTTP_ROUTE, route),
                            equalTo(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, "localhost"),
                            equalTo(ServerAttributes.SERVER_PORT, port),
                            equalTo(ClientAttributes.CLIENT_ADDRESS, "127.0.0.1"),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                val -> val.isInstanceOf(Long.class)))));
    assertThat(res.status().code()).isEqualTo(200);
  }
}
