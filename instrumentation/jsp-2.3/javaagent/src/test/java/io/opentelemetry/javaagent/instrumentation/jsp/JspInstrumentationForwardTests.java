/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsp;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.opentelemetry.api.common.Attributes;
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

  private static String baseUrl = "";

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
        JspInstrumentationForwardTests.class.getResource("/webapps/jsptest").getPath());

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
    AggregatedHttpResponse res = client.get("/" + forwardFromFileName).aggregate().join();
    String route = "/" + getContextPath() + "/" + forwardFromFileName;

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
                    span.hasName("Compile /" + forwardFromFileName)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp."
                                    + jspForwardFromClassPrefix
                                    + jspForwardFromClassName),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /" + forwardFromFileName)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.requestURL"), baseUrl + "/" + forwardFromFileName)),
                span ->
                    span.hasName("Compile /" + forwardDestFileName)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp."
                                    + jspForwardDestClassPrefix
                                    + jspForwardDestClassName),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /" + forwardDestFileName)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("jsp.forwardOrigin"), "/" + forwardFromFileName),
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/" + forwardDestFileName))));

    assertThat(res.status().code()).isEqualTo(200);
  }

  static class NonErroneousGetForwardArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "no java jsp",
              "forwards/forwardToNoJavaJsp.jsp",
              "nojava.jsp",
              "forwardToNoJavaJsp_jsp",
              "forwards.",
              "nojava_jsp",
              ""),
          Arguments.of(
              "normal java jsp",
              "forwards/forwardToSimpleJava.jsp",
              "common/loop.jsp",
              "forwardToSimpleJava_jsp",
              "forwards.",
              "loop_jsp",
              "common."));
    }
  }

  @Test
  void testNonErroneousGetForwardToPlainHtml() {
    AggregatedHttpResponse res = client.get("/forwards/forwardToHtml.jsp").aggregate().join();
    String route = "/" + getContextPath() + "/forwards/forwardToHtml.jsp";

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
                    span.hasName("Compile /forwards/forwardToHtml.jsp")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp.forwards.forwardToHtml_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /forwards/forwardToHtml.jsp")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/forwards/forwardToHtml.jsp"))));
    assertThat(res.status().code()).isEqualTo(200);
  }

  @Test
  void testNonErroneousGetForwardedToJspWithMultipleIncludes() {
    AggregatedHttpResponse res =
        client.get("/forwards/forwardToIncludeMulti.jsp").aggregate().join();
    String route = "/" + getContextPath() + "/forwards/forwardToIncludeMulti.jsp";

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
                    span.hasName("Compile /forwards/forwardToIncludeMulti.jsp")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp.forwards.forwardToIncludeMulti_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /forwards/forwardToIncludeMulti.jsp")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/forwards/forwardToIncludeMulti.jsp")),
                span ->
                    span.hasName("Compile /includes/includeMulti.jsp")
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp.includes.includeMulti_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /includes/includeMulti.jsp")
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.forwardOrigin"),
                                "/forwards/forwardToIncludeMulti.jsp"),
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/includes/includeMulti.jsp")),
                span ->
                    span.hasName("Compile /common/javaLoopH2.jsp")
                        .hasParent(trace.getSpan(4))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"), "org.apache.jsp.common.javaLoopH2_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /common/javaLoopH2.jsp")
                        .hasParent(trace.getSpan(4))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/includes/includeMulti.jsp"),
                            equalTo(
                                stringKey("jsp.forwardOrigin"),
                                "/forwards/forwardToIncludeMulti.jsp")),
                span ->
                    span.hasName("Compile /common/javaLoopH2.jsp")
                        .hasParent(trace.getSpan(4))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"), "org.apache.jsp.common.javaLoopH2_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /common/javaLoopH2.jsp")
                        .hasParent(trace.getSpan(4))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/includes/includeMulti.jsp"),
                            equalTo(
                                stringKey("jsp.forwardOrigin"),
                                "/forwards/forwardToIncludeMulti.jsp"))));
    assertThat(res.status().code()).isEqualTo(200);
  }

  @Test
  void testNonErroneousGetForwardToAnotherForward() {
    AggregatedHttpResponse res = client.get("/forwards/forwardToJspForward.jsp").aggregate().join();
    String route = "/" + getContextPath() + "/forwards/forwardToJspForward.jsp";

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
                    span.hasName("Compile /forwards/forwardToJspForward.jsp")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp.forwards.forwardToJspForward_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /forwards/forwardToJspForward.jsp")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/forwards/forwardToJspForward.jsp")),
                span ->
                    span.hasName("Compile /forwards/forwardToSimpleJava.jsp")
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp.forwards.forwardToSimpleJava_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /forwards/forwardToSimpleJava.jsp")
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.forwardOrigin"),
                                "/forwards/forwardToJspForward.jsp"),
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/forwards/forwardToSimpleJava.jsp")),
                span ->
                    span.hasName("Compile /common/loop.jsp")
                        .hasParent(trace.getSpan(4))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("jsp.classFQCN"), "org.apache.jsp.common.loop_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /common/loop.jsp")
                        .hasParent(trace.getSpan(4))
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("jsp.requestURL"), baseUrl + "/common/loop.jsp"),
                            equalTo(
                                stringKey("jsp.forwardOrigin"),
                                "/forwards/forwardToJspForward.jsp"))));
    assertThat(res.status().code()).isEqualTo(200);
  }

  @Test
  void testForwardToJspWithCompileErrorShouldNotProduceSecondRenderSpan() {
    AggregatedHttpResponse res =
        client.get("/forwards/forwardToCompileError.jsp").aggregate().join();
    String route = "/" + getContextPath() + "/forwards/forwardToCompileError.jsp";

    JasperException exception = new JasperException(new Exception());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + route)
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasStatus(StatusData.error())
                        .hasException(exception)
                        .hasAttributesSatisfyingExactly(
                            equalTo(UrlAttributes.URL_SCHEME, "http"),
                            equalTo(UrlAttributes.URL_PATH, route),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 500),
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
                    span.hasName("Compile /forwards/forwardToCompileError.jsp")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp.forwards.forwardToCompileError_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /forwards/forwardToCompileError_jsp")
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(exception)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/forwards/forwardToCompileError_jsp")),
                span ->
                    span.hasName("Compile /compileError.jsp")
                        .hasParent(trace.getSpan(2))
                        .hasStatus(StatusData.error())
                        .hasException(exception)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp.forwards.compileError_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler"))));
    assertThat(res.status().code()).isEqualTo(500);
  }

  @Test
  void testForwardToNonExistentJspShouldBe404() {
    AggregatedHttpResponse res =
        client.get("/forwards/forwardToNonExistent.jsp").aggregate().join();
    String route = "/" + getContextPath() + "/forwards/forwardToNonExistent.jsp";

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
                    span.hasName("Compile /forwards/forwardToNonExistent.jsp")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.classFQCN"),
                                "org.apache.jsp.forwards.forwardToNonExistent_jsp"),
                            equalTo(
                                stringKey("jsp.compiler"),
                                "org.apache.jasper.compiler.JDTCompiler")),
                span ->
                    span.hasName("Render /forwards/forwardToNonExistent.jps")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("jsp.requestURL"),
                                baseUrl + "/forwards/forwardToNonExistent.jsp")),
                span ->
                    span.hasName("ResponseFacade.sendError")
                        .hasParent(trace.getSpan(2))
                        .hasAttributes(Attributes.empty())));
    assertThat(res.status().code()).isEqualTo(404);
  }
}
