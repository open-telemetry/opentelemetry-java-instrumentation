/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
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

class JspInstrumentationForwardTests extends AbstractHttpServerUsingTest<Tomcat> {

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
        JspInstrumentationForwardTests.class.getResource("/webapps/jsptest").getPath());

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

  @ParameterizedTest(name = "Forward to {0}")
  @ArgumentsSource(NonErroneousGetForwardArgs.class)
  void testNonErroneousGetForwardTo(
      String name,
      String forwardFromFileName,
      String forwardDestFileName,
      String jspForwardFromClassName,
      String jspForwardFromClassPrefix,
      String jspForwardDestClassName,
      String jspForwardDestClassPrefix) {
    AggregatedHttpResponse res = client.get(forwardFromFileName).aggregate().join();
    assertThat(res.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + forwardFromFileName)
                            .withResponseStatus(200)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute(forwardFromFileName)
                            .withClassName(jspForwardFromClassPrefix + jspForwardFromClassName)
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute(forwardFromFileName)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute(forwardDestFileName)
                            .withClassName(jspForwardDestClassPrefix + jspForwardDestClassName)
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute(forwardDestFileName)
                            .build())));
  }

  static class NonErroneousGetForwardArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "no java jsp",
              "/forwards/forwardToNoJavaJsp.jsp",
              "/nojava.jsp",
              "forwardToNoJavaJsp_jsp",
              "forwards.",
              "nojava_jsp",
              ""),
          Arguments.of(
              "normal java jsp",
              "/forwards/forwardToSimpleJava.jsp",
              "/common/loop.jsp",
              "forwardToSimpleJava_jsp",
              "forwards.",
              "loop_jsp",
              "common."));
    }
  }

  @Test
  void testNonErroneousGetForwardToPlainHtml() {
    AggregatedHttpResponse res = client.get("/forwards/forwardToHtml.jsp").aggregate().join();
    assertThat(res.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + "/forwards/forwardToHtml.jsp")
                            .withResponseStatus(200)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToHtml.jsp")
                            .withClassName("forwards.forwardToHtml_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToHtml.jsp")
                            .build())));
  }

  @Test
  void testNonErroneousGetForwardedToJspWithMultipleIncludes() {
    AggregatedHttpResponse res =
        client.get("/forwards/forwardToIncludeMulti.jsp").aggregate().join();
    assertThat(res.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute(
                                "/" + getContextPath() + "/forwards/forwardToIncludeMulti.jsp")
                            .withResponseStatus(200)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToIncludeMulti.jsp")
                            .withClassName("forwards.forwardToIncludeMulti_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToIncludeMulti.jsp")
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/includes/includeMulti.jsp")
                            .withClassName("includes.includeMulti_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/includes/includeMulti.jsp")
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(4))
                            .withRoute("/common/javaLoopH2.jsp")
                            .withClassName("common.javaLoopH2_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(4))
                            .withRoute("/common/javaLoopH2.jsp")
                            .withRequestUrlOverride("/includes/includeMulti.jsp")
                            .withForwardOrigin("/forwards/forwardToIncludeMulti.jsp")
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(4))
                            .withRoute("/common/javaLoopH2.jsp")
                            .withClassName("common.javaLoopH2_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(4))
                            .withRoute("/common/javaLoopH2.jsp")
                            .withRequestUrlOverride("/includes/includeMulti.jsp")
                            .withForwardOrigin("/forwards/forwardToIncludeMulti.jsp")
                            .build())));
  }

  @Test
  void testNonErroneousGetForwardToAnotherForward() {
    AggregatedHttpResponse res = client.get("/forwards/forwardToJspForward.jsp").aggregate().join();
    assertThat(res.status().code()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute("/" + getContextPath() + "/forwards/forwardToJspForward.jsp")
                            .withResponseStatus(200)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToJspForward.jsp")
                            .withClassName("forwards.forwardToJspForward_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToJspForward.jsp")
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/forwards/forwardToSimpleJava.jsp")
                            .withClassName("forwards.forwardToSimpleJava_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/forwards/forwardToSimpleJava.jsp")
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(4))
                            .withRoute("/common/loop.jsp")
                            .withClassName("common.loop_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(4))
                            .withRoute("/common/loop.jsp")
                            .build())));
  }

  @Test
  void testForwardToJspWithCompileErrorShouldNotProduceSecondRenderSpan() {
    AggregatedHttpResponse res =
        client.get("/forwards/forwardToCompileError.jsp").aggregate().join();
    assertThat(res.status().code()).isEqualTo(500);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    spanAsserts.assertServerSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withMethod("GET")
                            .withRoute(
                                "/" + getContextPath() + "/forwards/forwardToCompileError.jsp")
                            .withResponseStatus(500)
                            .withExceptionClass(JasperException.class)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToCompileError.jsp")
                            .withClassName("forwards.forwardToCompileError_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToCompileError.jsp")
                            .withExceptionClass(JasperException.class)
                            .build()),
                span ->
                    spanAsserts.assertCompileSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(2))
                            .withRoute("/compileError.jsp")
                            .withClassName("compileError_jsp")
                            .withExceptionClass(JasperException.class)
                            .build())));
  }

  @Test
  void testForwardToNonExistentJspShouldBe404() {
    String route = "/" + getContextPath() + "/forwards/forwardToNonExistent.jsp";

    AggregatedHttpResponse res =
        client.get("/forwards/forwardToNonExistent.jsp").aggregate().join();
    assertThat(res.status().code()).isEqualTo(404);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + route)
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, route),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 404),
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
                            .withRoute("/forwards/forwardToNonExistent.jsp")
                            .withClassName("forwards.forwardToNonExistent_jsp")
                            .build()),
                span ->
                    spanAsserts.assertRenderSpan(
                        span,
                        new JspSpanAssertionBuilder()
                            .withParent(trace.getSpan(0))
                            .withRoute("/forwards/forwardToNonExistent.jsp")
                            .build()),
                span -> span.hasName("ResponseFacade.sendError").hasParent(trace.getSpan(2))));
  }
}
