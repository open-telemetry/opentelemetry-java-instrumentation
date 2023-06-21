/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import java.util.List;
import java.util.Set;

/** A builder of {@link HttpServerAttributesExtractor}. */
public final class HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter;
  final NetServerAttributesGetter<REQUEST, RESPONSE> netAttributesGetter;
  List<String> capturedRequestHeaders = emptyList();
  List<String> capturedResponseHeaders = emptyList();
  Set<String> knownMethods = HttpRequestMethodUtil.KNOWN_METHODS;

  HttpServerAttributesExtractorBuilder(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      NetServerAttributesGetter<REQUEST, RESPONSE> netAttributesGetter) {
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
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
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
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
      List<String> responseHeaders) {
    this.capturedResponseHeaders = responseHeaders;
    return this;
  }

  /**
   * Configures the extractor to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this extractor defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>. If an
   * unknown method is encountered, the extractor will use the value {@value
   * HttpRequestMethodUtil#_OTHER} instead of it and put the original value in an extra {@code
   * http.request.method_original} attribute.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   */
  @CanIgnoreReturnValue
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setKnownMethods(
      Set<String> knownMethods) {
    this.knownMethods = knownMethods;
    return this;
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractor} with the settings of this {@link
   * HttpServerAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new HttpServerAttributesExtractor<>(
        httpAttributesGetter,
        netAttributesGetter,
        capturedRequestHeaders,
        capturedResponseHeaders,
        knownMethods);
  }
}
