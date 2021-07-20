package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractHttpClientTest<REQUEST> {
  static final String BASIC_AUTH_KEY = "custom-authorization-header";
  static final String BASIC_AUTH_VAL = "plain text auth token";

  /**
   * Build the request to be passed to {@link #sendRequest(java.lang.Object, java.lang.String,
   * java.net.URI, java.util.Map)}.
   *
   * <p>By splitting this step out separate from {@code sendRequest}, tests and re-execute the same
   * request a second time to verify that the traceparent header is not added multiple times to the
   * request, and that the last one wins. Tests will fail if the header shows multiple times.
   */
  protected abstract REQUEST buildRequest(String method, URI uri, Map<String, String> headers);

  /**
   * Helper class for capturing result of asynchronous request and running a callback when result is
   * received.
   */
  protected static class RequestResult {
    private static final long timeout = 10_000;
    private final CountDownLatch valueReady = new CountDownLatch(1);
    private final Runnable callback;
    private int status;
    private Throwable throwable;

    public RequestResult(Runnable callback) {
      this.callback = callback;
    }

    public void complete(int status) {
      complete(() -> status, null);
    }

    public void complete(Throwable throwable) {
      complete(null, throwable);
    }

    public void complete(Supplier<Integer> status, Throwable throwable) {
      if (throwable != null) {
        this.throwable = throwable;
      } else {
        this.status = status.get();
      }
      callback.run();
      valueReady.countDown();
    }

    public int get() throws Throwable {
      if (!valueReady.await(timeout, TimeUnit.MILLISECONDS)) {
        throw new TimeoutException("Timed out waiting for response in " + timeout + "ms");
      }
      if (throwable != null) {
        throw throwable;
      }
      return status;
    }
  }

  /**
   * Make the request and return the status code of the response synchronously. Some clients, e.g.,
   * HTTPUrlConnection only support synchronous execution without callbacks, and many offer a
   * dedicated API for invoking synchronously, such as OkHttp's execute method. When implementing
   * this method, such an API should be used and the HTTP status code of the response returned, for
   * example: @Override int sendRequest(Request request, String method, URI uri, Map<String, String
   * headers = [:]) { HttpResponse response = client.execute(request) return response.statusCode() }
   */
  protected abstract int sendRequest(
      REQUEST request, String method, URI uri, Map<String, String> headers);

  protected void sendRequestWithCallback(
      REQUEST request,
      String method,
      URI uri,
      Map<String, String> headers,
      RequestResult requestResult) {
    // Must be implemented if testAsync is true
    throw new UnsupportedOperationException();
  }

  /** Returns the connection timeout that should be used when setting up tested clients. */
  protected final Duration connectTimeout() {
    return Duration.ofSeconds(5);
  }

  private InstrumentationExtension testing;
  private HttpClientTestServer server;

  @BeforeEach
  void verifyExtension() {
    if (testing == null) {
      throw new AssertionError(
          "Subclasses of AbstractHttpClientTest must register either "
              + "HttpClientLibraryInstrumentationExtension or "
              + "HttpClientAgentInstrumentationExtension");
    }
  }

  @Test
  final void successfulGetRequest() {
    URI uri = resolveAddress("/success");
    String method = "GET";
    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    List<List<SpanData>> traces = testing.waitForTraces(1);
    testing
        .waitAndAssertTraces(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        assertClientSpan(span, uri, method, responseCode)
                            .hasParentSpanId(SpanId.getInvalid()),
                    span ->
                        assertServerSpan(span).hasParentSpanId(traces.get(0).get(0).getSpanId())));
  }

  @Test
  final void successfulGetRequestWithParams() {
    URI uri = resolveAddress("/success?with=params");
    String method = "GET";
    int responseCode = doRequest(method, uri);

    assertThat(responseCode).isEqualTo(200);

    List<List<SpanData>> traces = testing.waitForTraces(1);
    testing
        .waitAndAssertTraces(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        assertClientSpan(span, uri, method, responseCode)
                            .hasParentSpanId(SpanId.getInvalid()),
                    span ->
                        assertServerSpan(span).hasParentSpanId(traces.get(0).get(0).getSpanId())));
  }

  @Test
  final void successfulPostRequestWithParent() {
    URI uri = resolveAddress("/success");
    String method = "POST";
    int responseCode = testing.runWithSpan("parent", () -> doRequest(method, uri));

    assertThat(responseCode).isEqualTo(200);

    List<List<SpanData>> traces = testing.waitForTraces(1);
    testing
        .waitAndAssertTraces(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("parent")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParentSpanId(SpanId.getInvalid()),
                    span ->
                        assertClientSpan(span, uri, method, responseCode)
                            .hasParentSpanId(traces.get(0).get(0).getSpanId()),
                    span ->
                        assertServerSpan(span).hasParentSpanId(traces.get(0).get(1).getSpanId())));
  }

  // @Test
  void successfulPutRequestWithParent() {
    URI uri = resolveAddress("/success");
    String method = "PUT";
    int responseCode = testing.runWithSpan("parent", () -> doRequest(method, uri));

    assertThat(responseCode).isEqualTo(200);

    List<List<SpanData>> traces = testing.waitForTraces(1);
    testing
        .waitAndAssertTraces(1)
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("parent")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParentSpanId(SpanId.getInvalid()),
                    span ->
                        assertClientSpan(span, uri, method, responseCode)
                            .hasParentSpanId(traces.get(0).get(0).getSpanId()),
                    span ->
                        assertServerSpan(span).hasParentSpanId(traces.get(0).get(1).getSpanId())));
  }

  private SpanDataAssert assertClientSpan(
      SpanDataAssert span, URI uri, String method, int responseCode) {
    Set<AttributeKey<?>> httpClientAttributes = httpAttributes(uri);
    return span.hasName(expectedClientSpanName(uri, method))
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfying(
            attrs -> {
              assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_NAME, uri.getHost());
              // TODO(anuraaga): Remove cast after
              // https://github.com/open-telemetry/opentelemetry-java/pull/3412
              assertThat(attrs)
                  .containsEntry(SemanticAttributes.NET_PEER_PORT, (long) uri.getPort());

              // Optional
              // TODO(anuraaga): Move to test knob rather than always treating
              // as optional
              if (attrs.asMap().containsKey(SemanticAttributes.NET_PEER_IP)) {
                assertThat(attrs).containsEntry(SemanticAttributes.NET_PEER_IP, "127.0.0.1");
              }

              if (httpClientAttributes.contains(SemanticAttributes.HTTP_URL)) {
                assertThat(attrs).containsEntry(SemanticAttributes.HTTP_URL, uri.toString());
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_METHOD)) {
                assertThat(attrs).containsEntry(SemanticAttributes.HTTP_METHOD, method);
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_FLAVOR)) {
                // TODO(anuraaga): Support HTTP/2
                assertThat(attrs)
                    .containsEntry(
                        SemanticAttributes.HTTP_FLAVOR,
                        SemanticAttributes.HttpFlavorValues.HTTP_1_1);
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_USER_AGENT)) {
                String userAgent = userAgent();
                if (userAgent != null) {
                  assertThat(attrs).containsEntry(SemanticAttributes.HTTP_USER_AGENT, userAgent);
                }
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_HOST)) {
                String host =
                    uri.getPort() != -1 ? uri.getHost() : uri.getHost() + ':' + uri.getPort();
                assertThat(attrs).containsEntry(SemanticAttributes.HTTP_HOST, host);
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH)) {
                assertThat(attrs)
                    .hasEntrySatisfying(
                        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                        length -> assertThat(length).isPositive());
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)) {
                assertThat(attrs)
                    .hasEntrySatisfying(
                        SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                        length -> assertThat(length).isPositive());
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_SCHEME)) {
                assertThat(attrs).containsEntry(SemanticAttributes.HTTP_SCHEME, uri.getScheme());
              }
              if (httpClientAttributes.contains(SemanticAttributes.HTTP_TARGET)) {
                String target = uri.getPath();
                if (uri.getQuery() != null) {
                  target += '?' + uri.getQuery();
                }
                assertThat(attrs).containsEntry(SemanticAttributes.HTTP_TARGET, target);
              }

              assertThat(attrs)
                  .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, (long) responseCode);
            });
  }

  private static SpanDataAssert assertServerSpan(SpanDataAssert span) {
    return span.hasName("test-http-server").hasKind(SpanKind.SERVER);
  }

  protected Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> attributes = new HashSet<>();
    attributes.add(SemanticAttributes.HTTP_URL);
    attributes.add(SemanticAttributes.HTTP_METHOD);
    attributes.add(SemanticAttributes.HTTP_FLAVOR);
    attributes.add(SemanticAttributes.HTTP_USER_AGENT);
    return attributes;
  }

  protected String expectedClientSpanName(URI uri, String method) {
    return method != null ? "HTTP " + method : "HTTP request";
  }

  @Nullable
  protected String userAgent() {
    return null;
  }

  private int doRequest(String method, URI uri) {
    return doRequest(method, uri, Collections.emptyMap());
  }

  private int doRequest(String method, URI uri, Map<String, String> headers) {
    REQUEST request = buildRequest(method, uri, headers);
    return sendRequest(request, method, uri, headers);
  }

  private URI resolveAddress(String path) {
    return URI.create("http://localhost:" + server.httpPort() + path);
  }

  final void setTesting(InstrumentationExtension testing, HttpClientTestServer server) {
    this.testing = testing;
    this.server = server;
  }
}
