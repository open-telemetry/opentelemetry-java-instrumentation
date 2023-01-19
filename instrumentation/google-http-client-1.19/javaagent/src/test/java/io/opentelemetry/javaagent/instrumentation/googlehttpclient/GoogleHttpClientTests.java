package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptionsBuilder;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTests;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTypeAdapter;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

// TODO: This replaces the abstract client test base class
public class GoogleHttpClientTests {

  private final HttpClientTests<HttpRequest> delegate;
  private final HttpClientTypeAdapter<HttpRequest> adapter;

  private GoogleHttpClientTests(HttpClientTests<HttpRequest> delegate,
      HttpClientTypeAdapter<HttpRequest> adapter) {
    this.delegate = delegate;
    this.adapter = adapter;
  }

  public static GoogleHttpClientTests create(HttpClientTypeAdapter<HttpRequest> adapter,
      InstrumentationTestRunner testRunner, HttpClientTestServer server) {
    HttpClientTestOptions options = buildOptions();
    HttpClientTests<HttpRequest> clientTests = new HttpClientTests<>(testRunner, server, options, adapter);
    return new GoogleHttpClientTests(clientTests, adapter);
  }

  List<DynamicTest> all() {
    return Stream.concat(
            delegate.all(),
            Stream.of(errorTracesWhenExceptionIsNotThrown()))
        .collect(Collectors.toList());
  }

  DynamicTest errorTracesWhenExceptionIsNotThrown() {
    return delegate.test("error traces when exception is not thrown", () -> {

      URI uri = delegate.resolveAddress("/error");

      HttpRequest request = adapter.buildRequest("GET", uri, Collections.emptyMap());
      int responseCode = adapter.sendRequest(request, "GET", uri, Collections.emptyMap());

      assertThat(responseCode).isEqualTo(500);
      delegate.getTestRunner().waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.CLIENT)
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfying(
                              attrs ->
                                  OpenTelemetryAssertions.assertThat(attrs)
                                      .hasSize(8)
                                      .containsEntry(
                                          SemanticAttributes.NET_TRANSPORT,
                                          SemanticAttributes.NetTransportValues.IP_TCP)
                                      .containsEntry(SemanticAttributes.NET_PEER_NAME, "localhost")
                                      .hasEntrySatisfying(
                                          SemanticAttributes.NET_PEER_PORT,
                                          port -> assertThat(port).isPositive())
                                      .containsEntry(SemanticAttributes.HTTP_URL, uri.toString())
                                      .containsEntry(SemanticAttributes.HTTP_METHOD, "GET")
                                      .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 500)
                                      .hasEntrySatisfying(
                                          SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                                          length -> assertThat(length).isPositive())
                                      .containsEntry(
                                          SemanticAttributes.HTTP_FLAVOR,
                                          SemanticAttributes.HttpFlavorValues.HTTP_1_1)),
                  span -> span.hasKind(SpanKind.SERVER).hasParent(trace.getSpan(0))));
    });
  }

  static HttpClientTestOptions buildOptions() {
    HttpClientTestOptionsBuilder builder = HttpClientTestOptions.builder();

    // executeAsync does not actually allow asynchronous execution since it returns a standard
    // Future which cannot have callbacks attached. We instrument execute and executeAsync
    // differently so test both but do not need to run our normal asynchronous tests, which check
    // context propagation, as there is no possible context propagation.
    builder.disableTestCallback();
    builder.enableTestReadTimeout();

    // Circular redirects don't throw an exception with Google Http Client
    builder.disableTestCircularRedirects();
    return builder.build();
  }

}
