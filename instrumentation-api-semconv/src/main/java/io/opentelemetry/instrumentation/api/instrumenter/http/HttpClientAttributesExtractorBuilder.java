/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.util.List;

/** A builder of {@link HttpClientAttributesExtractor}. */
public final class HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter;
  final NetClientAttributesGetter<REQUEST, RESPONSE> netAttributesGetter;
  List<String> capturedRequestHeaders = emptyList();
  List<String> capturedResponseHeaders = emptyList();

  HttpClientAttributesExtractorBuilder(
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetClientAttributesGetter<REQUEST, RESPONSE> netAttributesGetter) {
    this.httpAttributesGetter = httpAttributesGetter;
    this.netAttributesGetter = netAttributesGetter;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes as described in <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-request-and-response-headers">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP request header values will be captured under the {@code http.request.header.<name>}
   * attribute key. The {@code <name>} part in the attribute key is the normalized header name:
   * lowercase, with dashes replaced by underscores.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
      List<String> requestHeaders) {
    this.capturedRequestHeaders = requestHeaders;
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes as described in
   * <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-request-and-response-headers">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP response header values will be captured under the {@code
   * http.response.header.<name>} attribute key. The {@code <name>} part in the attribute key is the
   * normalized header name: lowercase, with dashes replaced by underscores.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
      List<String> responseHeaders) {
    this.capturedResponseHeaders = responseHeaders;
    return this;
  }

  /**
   * Returns a new {@link HttpClientAttributesExtractor} with the settings of this {@link
   * HttpClientAttributesExtractorBuilder}.
   */
  public HttpClientAttributesExtractor<REQUEST, RESPONSE> build() {
    return new HttpClientAttributesExtractor<>(
        httpAttributesGetter, netAttributesGetter, capturedRequestHeaders, capturedResponseHeaders);
  }
}
