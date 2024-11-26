/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.jakarta;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpRequest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpMethod;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.QueryParams;
import io.opentelemetry.testing.internal.armeria.common.RequestHeaders;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public abstract class BaseJsfTest extends AbstractHttpServerUsingTest<Server> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @BeforeAll
  protected void setUp() {
    startServer();
  }

  @AfterAll
  protected void cleanUp() {
    cleanupServer();
  }

  @Override
  protected Server setupServer() throws Exception {
    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath(getContextPath());
    // set up test application
    webAppContext.setBaseResource(Resource.newSystemResource("test-app"));

    Resource extraResource = Resource.newSystemResource("test-app-extra");
    if (extraResource != null) {
      webAppContext.getMetaData().addWebInfResource(extraResource);
    }

    Server jettyServer = new Server(port);
    jettyServer.setHandler(webAppContext);
    jettyServer.start();

    return jettyServer;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  protected String getContextPath() {
    return "/jetty-context";
  }

  @ParameterizedTest
  @ArgumentsSource(PathTestArgs.class)
  void testPath(String path, String route) {
    AggregatedHttpResponse response =
        client.get(address.resolve(path).toString()).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8().trim()).isEqualTo("Hello");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getContextPath() + "/hello.xhtml")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isInstanceOf(Long.class)),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_PATH, getContextPath() + "/" + path),
                            equalTo(USER_AGENT_ORIGINAL, TEST_USER_AGENT),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(HTTP_ROUTE, getContextPath() + "/" + route),
                            satisfies(
                                CLIENT_ADDRESS,
                                val ->
                                    val.satisfiesAnyOf(
                                        v -> assertThat(v).isEqualTo(TEST_CLIENT_IP),
                                        v -> assertThat(v).isNull())))));
  }

  static class PathTestArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of("hello.xhtml", "*.xhtml"), Arguments.of("faces/hello.xhtml", "faces/*"));
    }
  }

  @Test
  void testGreeting() {
    AggregatedHttpResponse response =
        client.get(address.resolve("greeting.xhtml").toString()).aggregate().join();
    Document doc = Jsoup.parse(response.contentUtf8());

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(doc.selectFirst("title").text()).isEqualTo("Hello, World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getContextPath() + "/greeting.xhtml")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()));

    testing.clearData();

    String viewState = doc.selectFirst("[name=jakarta.faces.ViewState]").val();
    String formAction = doc.selectFirst("#app-form").attr("action");
    String jsessionid =
        formAction.substring(formAction.indexOf("jsessionid=") + "jsessionid=".length());

    assertThat(viewState).isNotNull();
    assertThat(jsessionid).isNotNull();

    // set up form parameter for post
    QueryParams formBody =
        QueryParams.builder()
            .add("app-form", "app-form")
            // value used for name is returned in app-form:output-message element
            .add("app-form:name", "test")
            .add("app-form:submit", "Say hello")
            .add("app-form_SUBMIT", "1") // MyFaces
            .add("jakarta.faces.ViewState", viewState)
            .build();

    // use the session created for first request
    AggregatedHttpRequest request2 =
        AggregatedHttpRequest.of(
            RequestHeaders.builder(
                    HttpMethod.POST,
                    address.resolve("greeting.xhtml;jsessionid=" + jsessionid).toString())
                .contentType(MediaType.FORM_DATA)
                .build(),
            HttpData.ofUtf8(formBody.toQueryString()));
    AggregatedHttpResponse response2 = client.execute(request2).aggregate().join();
    String responseContent = response2.contentUtf8();
    Document doc2 = Jsoup.parse(responseContent);

    assertThat(response2.status().code()).isEqualTo(200);
    assertThat(doc2.getElementById("app-form:output-message").text()).isEqualTo("Hello test");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getContextPath() + "/greeting.xhtml")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent(),
                span -> handlerSpan(trace, 0, "#{greetingForm.submit()}", null)));
  }

  @Test
  void testException() {
    // we need to display the page first before posting data to it
    AggregatedHttpResponse response =
        client.get(address.resolve("greeting.xhtml").toString()).aggregate().join();
    Document doc = Jsoup.parse(response.contentUtf8());

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(doc.selectFirst("title").text()).isEqualTo("Hello, World!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getContextPath() + "/greeting.xhtml")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()));

    testing.clearData();

    String viewState = doc.selectFirst("[name=jakarta.faces.ViewState]").val();
    String formAction = doc.selectFirst("#app-form").attr("action");
    String jsessionid =
        formAction.substring(formAction.indexOf("jsessionid=") + "jsessionid=".length());

    assertThat(viewState).isNotNull();
    assertThat(jsessionid).isNotNull();

    // set up form parameter for post
    QueryParams formBody =
        QueryParams.builder()
            .add("app-form", "app-form")
            // setting name parameter to "exception" triggers throwing exception in GreetingForm
            .add("app-form:name", "exception")
            .add("app-form:submit", "Say hello")
            .add("app-form_SUBMIT", "1") // MyFaces
            .add("jakarta.faces.ViewState", viewState)
            .build();

    // use the session created for first request
    AggregatedHttpRequest request2 =
        AggregatedHttpRequest.of(
            RequestHeaders.builder(
                    HttpMethod.POST,
                    address.resolve("greeting.xhtml;jsessionid=" + jsessionid).toString())
                .contentType(MediaType.FORM_DATA)
                .build(),
            HttpData.ofUtf8(formBody.toQueryString()));

    AggregatedHttpResponse response2 = client.execute(request2).aggregate().join();
    assertThat(response2.status().code()).isEqualTo(500);

    IllegalStateException expectedException = new IllegalStateException("submit exception");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getContextPath() + "/greeting.xhtml")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(expectedException),
                span -> handlerSpan(trace, 0, "#{greetingForm.submit()}", expectedException)));
  }

  List<Consumer<SpanDataAssert>> handlerSpan(
      TraceAssert trace, int parentIndex, String spanName, Exception expectedException) {
    List<Consumer<SpanDataAssert>> assertions =
        new ArrayList<>(
            Arrays.asList(
                span ->
                    span.hasName(spanName)
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(parentIndex))));

    if (expectedException != null) {
      assertions.add(span -> span.hasStatus(StatusData.error()).hasException(expectedException));
    }
    return assertions;
  }
}
