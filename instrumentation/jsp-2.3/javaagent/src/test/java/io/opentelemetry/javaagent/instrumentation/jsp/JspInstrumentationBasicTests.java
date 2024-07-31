/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
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

  private static JspSpanAssertions spanAsserts;

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

    String baseUrl = "http://localhost:" + port + "/" + getContextPath();
    spanAsserts = new JspSpanAssertions(baseUrl, port);
    client = WebClient.of(baseUrl);

    tomcatServer.addWebapp(
        "/" + getContextPath(),
        JspInstrumentationBasicTests.class.getResource("/webapps/jsptest").getPath());

    tomcatServer.start();
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
    assertThat(res.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + jspFileName)
                            .withResponseStatus(200)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute(jspFileName)
                            .withClassName(jspClassNamePrefix + jspClassName)
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute(jspFileName)
                            .build())));
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
    String route = "/" + getContextPath() + "/getQuery.jsp";

    AggregatedHttpResponse res = client.get("/getQuery.jsp?" + queryString).aggregate().join();
    assertThat(res.status().code()).isEqualTo(200);

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
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/getQuery.jsp")
                            .withClassName("getQuery_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/getQuery.jsp")
                            .build())));
  }

  @Test
  void testNonErroneousPost() {
    RequestHeaders headers =
        RequestHeaders.builder(HttpMethod.POST, "/post.jsp")
            .contentType(MediaType.FORM_DATA)
            .build();

    AggregatedHttpResponse res = client.execute(headers, "name=world").aggregate().join();
    assertThat(res.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("POST")
                            .withRoute("/" + getContextPath() + "/post.jsp")
                            .withResponseStatus(200)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/post.jsp")
                            .withClassName("post_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/post.jsp")
                            .build())));
  }

  @ParameterizedTest(name = "GET jsp with {0}")
  @ArgumentsSource(ErroneousRuntimeErrorsArgs.class)
  void testErroneousRuntimeErrorsGet(
      String testName,
      String jspFileName,
      String jspClassName,
      Class<?> exceptionClass,
      boolean errorMessageOptional) {
    AggregatedHttpResponse res = client.get(jspFileName).aggregate().join();
    assertThat(res.status().code()).isEqualTo(500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + jspFileName)
                            .withResponseStatus(500)
                            .withExceptionClass(exceptionClass)
                            .withErrorMessageOptional(errorMessageOptional)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute(jspFileName)
                            .withClassName(jspClassName)
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute(jspFileName)
                            .withErrorMessageOptional(errorMessageOptional)
                            .build())));
  }

  static class ErroneousRuntimeErrorsArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "java runtime error",
              "/runtimeError.jsp",
              "runtimeError_jsp",
              ArithmeticException.class,
              false),
          Arguments.of(
              "invalid write",
              "/invalidWrite.jsp",
              "invalidWrite_jsp",
              IndexOutOfBoundsException.class,
              true),
          Arguments.of(
              "invalid write", "/getQuery.jsp", "getQuery_jsp", NullPointerException.class, true));
    }
  }

  @Test
  void testNonErroneousIncludePlainHtmlGet() {
    AggregatedHttpResponse res = client.get("/includes/includeHtml.jsp").aggregate().join();
    assertThat(res.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + "/includes/includeHtml.jsp")
                            .withResponseStatus(200)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/includes/includeHtml.jsp")
                            .withClassName("includes.includeHtml_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/includes/includeHtml.jsp")
                            .build())));
  }

  @Test
  void testNonErroneousMultiGet() {
    AggregatedHttpResponse res = client.get("/includes/includeMulti.jsp").aggregate().join();
    assertThat(res.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + "/includes/includeMulti.jsp")
                            .withResponseStatus(200)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/includes/includeMulti.jsp")
                            .withClassName("includes.includeMulti_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/includes/includeMulti.jsp")
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/common/javaLoopH2.jsp")
                            .withClassName("common.javaLoopH2_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/common/javaLoopH2.jsp")
                            .withRequestUrlOverride("/includes/includeMulti.jsp")
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/common/javaLoopH2.jsp")
                            .withClassName("common.javaLoopH2_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/common/javaLoopH2.jsp")
                            .withRequestUrlOverride("/includes/includeMulti.jsp")
                            .build())));
  }

  @ParameterizedTest
  @ArgumentsSource(CompileErrorsArgs.class)
  void testCompileErrorShouldNotProduceRenderTracesAndSpans(
      String jspFileName, String jspClassName, String jspClassNamePrefix) {
    AggregatedHttpResponse res = client.get(jspFileName).aggregate().join();
    assertThat(res.status().code()).isEqualTo(500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + jspFileName)
                            .withResponseStatus(500)
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
    assertThat(res.status().code()).isEqualTo(200);

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
  }
}
