/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.testing.util.TestServerServiceMapping.expectedMappedServiceName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.UrlIncubatingAttributes.URL_TEMPLATE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;

public abstract class AbstractSpringWebfluxClientInstrumentationTest
    extends AbstractHttpClientTest<WebClient.RequestBodySpec> {

  private static final MethodHandle GET_STATUS_CODE;
  private static final MethodHandle STATUS_CODE_VALUE;

  static {
    MethodHandle getStatusCode;
    MethodHandle statusCodeValue;
    Class<?> httpStatusCodeClass;

    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    try {
      httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatusCode");
    } catch (ClassNotFoundException e) {
      try {
        httpStatusCodeClass = Class.forName("org.springframework.http.HttpStatus");
      } catch (ClassNotFoundException f) {
        throw new LinkageError("Did not find neither HttpStatus nor HttpStatusCode class", f);
      }
    }

    try {
      getStatusCode =
          lookup.findVirtual(
              ClientResponse.class, "statusCode", MethodType.methodType(httpStatusCodeClass));
      statusCodeValue =
          lookup.findVirtual(httpStatusCodeClass, "value", MethodType.methodType(int.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new LinkageError("Did not find statusCode() method", e);
    }

    GET_STATUS_CODE = getStatusCode;
    STATUS_CODE_VALUE = statusCodeValue;
  }

  @Override
  public WebClient.RequestBodySpec buildRequest(
      String method, URI uri, Map<String, String> headers) {

    WebClient webClient =
        instrument(WebClient.builder().clientConnector(ClientHttpConnectorFactory.create()))
            .build();

    return webClient
        .method(HttpMethod.valueOf(method))
        .uri(uri)
        .headers(h -> headers.forEach(h::add));
  }

  protected abstract WebClient.Builder instrument(WebClient.Builder builder);

  protected abstract boolean supportsUriTemplates();

  protected abstract boolean includesBasePathInUriTemplate();

  protected static boolean emitExperimentalTelemetry() {
    return Boolean.getBoolean("otel.instrumentation.http.client.emit-experimental-telemetry");
  }

  @Test
  void shouldCaptureUriTemplateFromVarargs() {
    WebClient client = createWebClient();

    URI uri = resolveAddress("/hello/some-account");
    String template = expectedTemplate("/hello/{id}");
    String instrumentationName =
        assertTemplateRequest(client.get().uri("/hello/{id}", "some-account"), uri, template);

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("http.client.request.duration")
                .hasDescription("Duration of HTTP client requests.")
                .hasUnit("s")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .hasSumGreaterThan(0.0)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(HTTP_REQUEST_METHOD, "GET"),
                                        equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                        equalTo(SERVER_ADDRESS, "localhost"),
                                        equalTo(SERVER_PORT, uri.getPort()),
                                        satisfies(
                                            NETWORK_PROTOCOL_VERSION, val -> val.isIn(null, "1.1")),
                                        equalTo(URL_TEMPLATE, template)))));
  }

  @Test
  void shouldCaptureUriTemplateFromMap() {
    WebClient client = createWebClient();

    assertTemplateRequest(
        client.get().uri("/hello/{id}", singletonMap("id", "some-account")),
        resolveAddress("/hello/some-account"),
        expectedTemplate("/hello/{id}"));
  }

  @Test
  void shouldCaptureUriTemplateFromAbsoluteUri() {
    WebClient client = createWebClient();

    assertTemplateRequest(
        client.get().uri(resolveAddress("/hello/") + "{id}", "some-account"),
        resolveAddress("/hello/some-account"),
        expectedTemplate("/hello/{id}"));
  }

  @Test
  void shouldCapturePathTemplateWithTemplatedSchemeAndUserInfo() {
    assertTemplateWithUserInfo(
        "{scheme}://user:secret@example.com/accounts/{id}",
        "https",
        expectedTemplate("/accounts/{id}"));
  }

  @Test
  void shouldNotCaptureOpaqueTemplateWithSchemeDelimiterInVariable() {
    // Older Spring defaults cannot expand opaque URI templates.
    assumeTrue(supportsUriTemplates());
    assertTemplateWithUserInfo(
        "{scheme}user:secret@example.com/accounts/{id}", "https://", expectedTemplate(null));
  }

  @Test
  void shouldNotCaptureAmbiguousUsernameOnlyUserInfoTemplate() {
    assumeTrue(supportsUriTemplates());
    DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
    uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
    ClientHttpConnector connector = ClientHttpConnectorFactory.create();
    WebClient client =
        instrument(
                WebClient.builder()
                    .uriBuilderFactory(uriBuilderFactory)
                    .clientConnector(
                        (method, uri, callback) ->
                            connector.connect(
                                method, resolveAddress("/hello/some-account"), callback)))
            .build();

    client
        .get()
        .uri("{scheme}api-token@example.com/accounts/{id}", "https://", "some-account")
        .retrieve()
        .bodyToMono(String.class)
        .block();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(
                                HTTP_RESPONSE_BODY_SIZE, emitExperimentalTelemetry() ? 20L : null),
                            equalTo(
                                URL_FULL,
                                "https://REDACTED:REDACTED@example.com/accounts/some-account"),
                            equalTo(URL_TEMPLATE, null),
                            equalTo(SERVER_ADDRESS, "example.com"),
                            equalTo(SERVER_PORT, 443),
                            satisfies(NETWORK_PROTOCOL_VERSION, val -> val.isIn(null, "1.1"))),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))));
  }

  private void assertTemplateWithUserInfo(
      String template, String scheme, String expectedPathTemplate) {
    ClientHttpConnector connector = ClientHttpConnectorFactory.create();
    WebClient client =
        createWebClient()
            .mutate()
            // Older connectors cannot handle URI userinfo; route to the existing test server.
            .clientConnector(
                (method, uri, callback) ->
                    connector.connect(method, resolveAddress("/hello/some-account"), callback))
            .build();
    client.get().uri(template, scheme, "some-account").retrieve().bodyToMono(String.class).block();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            expectedPathTemplate == null ? "GET" : "GET " + expectedPathTemplate)
                        .hasKind(CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(
                                HTTP_RESPONSE_BODY_SIZE, emitExperimentalTelemetry() ? 20L : null),
                            equalTo(
                                URL_FULL,
                                "https://REDACTED:REDACTED@example.com/accounts/some-account"),
                            equalTo(URL_TEMPLATE, expectedPathTemplate),
                            equalTo(SERVER_ADDRESS, "example.com"),
                            equalTo(SERVER_PORT, 443),
                            satisfies(NETWORK_PROTOCOL_VERSION, val -> val.isIn(null, "1.1"))),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void shouldCaptureUriTemplateAndRedactSignedQuery() {
    WebClient client = createWebClient();

    assertTemplateRequest(
        client.get().uri("/hello/{id}?sig=secret", "some-account"),
        resolveAddress("/hello/some-account?sig=REDACTED"),
        expectedTemplate("/hello/{id}?sig=secret"));
  }

  @Test
  void shouldNotCaptureUriTemplateFromStringWithoutVariables() {
    WebClient client = createWebClient();

    assertTemplateRequest(
        client.get().uri("/hello/some-account?sig=secret"),
        resolveAddress("/hello/some-account?sig=REDACTED"),
        expectedTemplate(null));
  }

  @Test
  void shouldCaptureUriTemplateWithQueryAndFragment() {
    WebClient client = createWebClient();

    assertTemplateRequest(
        client
            .get()
            .uri(
                "/hello/{id}?first=one&second={id}#section/{id}",
                singletonMap("id", "some-account")),
        resolveAddress("/hello/some-account?first=one&second=some-account#section/some-account"),
        expectedTemplate("/hello/{id}?first=one&second={id}#section/{id}"));
  }

  @Test
  void shouldNotCaptureUriTemplateFromUriObject() {
    WebClient client = createWebClient();

    URI uri = resolveAddress("/hello/some-account");
    assertTemplateRequest(client.get().uri(uri), uri, expectedTemplate(null));
  }

  @Test
  void shouldNotCaptureUriTemplateFromUriBuilder() {
    WebClient client = createWebClient();

    assertTemplateRequest(
        client.get().uri(builder -> builder.path("/hello/{id}").build("some-account")),
        resolveAddress("/hello/some-account"),
        expectedTemplate(null));
  }

  private WebClient createWebClient() {
    return instrument(
            WebClient.builder()
                .baseUrl(resolveAddress("").toString())
                .clientConnector(ClientHttpConnectorFactory.create()))
        .build();
  }

  @Test
  void shouldCaptureUriTemplateWithBasePath() {
    String baseUrl = resolveAddress("/hello").toString();
    WebClient client =
        instrument(
                WebClient.builder()
                    .baseUrl(baseUrl)
                    .clientConnector(ClientHttpConnectorFactory.create()))
            .build();
    assertTemplateRequest(
        client.get().uri("/{id}", "some-account"),
        resolveAddress("/hello/some-account"),
        expectedTemplate(includesBasePathInUriTemplate() ? "/hello/{id}" : "/{id}"));
  }

  protected String expectedTemplate(String template) {
    if (template == null || !emitExperimentalTelemetry() || !supportsUriTemplates()) {
      return null;
    }
    return template;
  }

  protected String assertTemplateRequest(
      WebClient.RequestHeadersSpec<?> request, URI uri, String template) {
    request.retrieve().bodyToMono(String.class).block();

    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName(template == null ? "GET" : "GET " + template)
                      .hasKind(CLIENT)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(
                          equalTo(HTTP_REQUEST_METHOD, "GET"),
                          equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                          equalTo(
                              HTTP_RESPONSE_BODY_SIZE, emitExperimentalTelemetry() ? 20L : null),
                          equalTo(URL_FULL, uri.toString()),
                          equalTo(URL_TEMPLATE, template),
                          equalTo(SERVER_ADDRESS, "localhost"),
                          equalTo(SERVER_PORT, uri.getPort()),
                          satisfies(NETWORK_PROTOCOL_VERSION, val -> val.isIn(null, "1.1")),
                          equalTo(stringKey("server.service.name"), expectedMappedServiceName())),
              span ->
                  span.hasName("test-http-server")
                      .hasKind(SpanKind.SERVER)
                      .hasParent(trace.getSpan(0)));
        });
    return instrumentationName.get();
  }

  @Override
  public int sendRequest(
      WebClient.RequestBodySpec request, String method, URI uri, Map<String, String> headers) {
    if (Webflux7Util.IS_WEBFLUX_7) {
      return Webflux7Util.doRequest(request);
    }

    ClientResponse response = requireNonNull(request.exchange().block());
    return getStatusCode(response);
  }

  @Override
  public void sendRequestWithCallback(
      WebClient.RequestBodySpec request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    if (Webflux7Util.IS_WEBFLUX_7) {
      Webflux7Util.sendRequestWithCallback(
          request, httpClientResult::complete, httpClientResult::complete);
    } else {
      request
          .exchange()
          .subscribe(
              response -> httpClientResult.complete(getStatusCode(response)),
              httpClientResult::complete);
    }
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    optionsBuilder.disableTestRedirects();

    // no enum value for non standard method
    optionsBuilder.disableTestNonStandardHttpMethod();

    // timeouts leak the scope
    optionsBuilder.disableTestReadTimeout();

    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(NETWORK_PROTOCOL_VERSION);
          return attributes;
        });

    optionsBuilder.setClientSpanErrorMapper(
        (uri, throwable) -> {
          if (!throwable.getClass().getName().endsWith("WebClientRequestException")) {
            String uriString = uri.toString();
            if (uriString.equals("http://localhost:61/")) { // unopened port
              if (!throwable.getClass().getName().endsWith("AnnotatedConnectException")) {
                throwable = throwable.getCause();
              }
            } else if (uriString.equals("https://192.0.2.1/")) { // non routable address
              throwable = throwable.getCause();
            }
          }
          return throwable;
        });

    optionsBuilder.setSingleConnectionFactory(
        (host, port) -> new SpringWebfluxSingleConnection(host, port, this::instrument));
  }

  private static int getStatusCode(ClientResponse response) {
    try {
      Object statusCode = GET_STATUS_CODE.invoke(response);
      return (int) STATUS_CODE_VALUE.invoke(statusCode);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  @Test
  void shouldEndSpanOnMonoTimeout() {
    URI uri = resolveAddress("/read-timeout");
    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      WebClient.RequestBodySpec request = buildRequest("GET", uri, emptyMap());
                      Mono<ClientResponse> mono;
                      if (Webflux7Util.IS_WEBFLUX_7) {
                        mono = Webflux7Util.exchangeToMono(request);
                      } else {
                        mono = request.exchange();
                      }
                      // apply Mono timeout that is way shorter than HTTP request timeout
                      return mono.timeout(Duration.ofSeconds(1)).block();
                    }));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(emitExceptionAsSpanEvents() ? thrown : null),
                span ->
                    span.hasName("GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(URL_FULL, uri.toString()),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, uri.getPort()),
                            equalTo(stringKey("server.service.name"), expectedMappedServiceName()),
                            equalTo(ERROR_TYPE, "cancelled")),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(1))));
  }
}
