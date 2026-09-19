/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HostAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.ServerAddressAndPortExtractor;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * A builder of {@link HttpClientAttributesExtractor}.
 *
 * @since 2.0.0
 */
public final class HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter;

  final AddressAndPortExtractor<REQUEST> serverAddressAndPortExtractor;
  CapturedHttpHeaders capturedRequestHeaders = CapturedHttpHeaders.create("request", null);
  CapturedHttpHeaders capturedResponseHeaders = CapturedHttpHeaders.create("response", null);
  Set<String> knownMethods = HttpConstants.KNOWN_METHODS;
  ToIntFunction<Context> resendCountIncrementer = HttpClientRequestResendCount::getAndIncrement;
  Set<String> sensitiveQueryParameters = HttpConstants.SENSITIVE_QUERY_PARAMETERS;

  static {
    Experimental.internalSetClientSensitiveQueryParameters(
        (builder, params) -> builder.sensitiveQueryParameters = params);
  }

  HttpClientAttributesExtractorBuilder(
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    this.httpAttributesGetter = httpAttributesGetter;
    serverAddressAndPortExtractor =
        new ServerAddressAndPortExtractor<>(
            httpAttributesGetter, new HostAddressAndPortExtractor<>(httpAttributesGetter));
  }

  /**
   * Configures which HTTP request headers are captured as span attributes, as described in <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#http-client">HTTP
   * semantic conventions</a>.
   *
   * <p>Header values are captured under the {@code http.request.header.<key>} attribute key. The
   * {@code <key>} part in the attribute key is the lowercase header name.
   *
   * <p>Selector patterns are matched case-insensitively, since HTTP header names are
   * case-insensitive. {@code ?} matches one character and {@code *} matches any number of
   * characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded, and an
   * {@linkplain IncludeExclude#isEmpty() empty} selector captures no headers.
   *
   * <p>Header names that are not listed as exact included names are resolved through {@link
   * HttpCommonAttributesGetter#getHttpRequestHeaderNames(Object)}, so wildcard and exclude-only
   * selectors only capture headers when the getter implements that method.
   *
   * @since 2.31.0
   */
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setRequestHeaders(
      IncludeExclude requestHeaders) {
    this.capturedRequestHeaders = CapturedHttpHeaders.create("request", requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes as described in <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#http-client">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP request header values will be captured under the {@code http.request.header.<key>}
   * attribute key. The {@code <key>} part in the attribute key is the lowercase header name.
   *
   * <p>The header names are matched literally, so {@code *} and {@code ?} are not treated as glob
   * patterns.
   *
   * @param requestHeaders A list of HTTP header names.
   * @deprecated Use {@link #setRequestHeaders(IncludeExclude)} instead. To be removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
      Collection<String> requestHeaders) {
    this.capturedRequestHeaders = CapturedHttpHeaders.createExact("request", requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes as described in <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#http-client">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP request header values will be captured under the {@code http.request.header.<key>}
   * attribute key. The {@code <key>} part in the attribute key is the lowercase header name.
   *
   * <p>The header names are matched literally, so {@code *} and {@code ?} are not treated as glob
   * patterns.
   *
   * @param requestHeaders A list of HTTP header names.
   * @deprecated Use {@link #setRequestHeaders(IncludeExclude)} instead. To be removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
      List<String> requestHeaders) {
    return setCapturedRequestHeaders((Collection<String>) requestHeaders);
  }

  /**
   * Configures which HTTP response headers are captured as span attributes, as described in <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#common-attributes">HTTP
   * semantic conventions</a>.
   *
   * <p>Header values are captured under the {@code http.response.header.<key>} attribute key. The
   * {@code <key>} part in the attribute key is the lowercase header name.
   *
   * <p>Selector patterns are matched case-insensitively, since HTTP header names are
   * case-insensitive. {@code ?} matches one character and {@code *} matches any number of
   * characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded, and an
   * {@linkplain IncludeExclude#isEmpty() empty} selector captures no headers.
   *
   * <p>Header names that are not listed as exact included names are resolved through {@link
   * HttpCommonAttributesGetter#getHttpResponseHeaderNames(Object, Object)}, so wildcard and
   * exclude-only selectors only capture headers when the getter implements that method.
   *
   * @since 2.31.0
   */
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setResponseHeaders(
      IncludeExclude responseHeaders) {
    this.capturedResponseHeaders = CapturedHttpHeaders.create("response", responseHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes as described in
   * <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#common-attributes">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP response header values will be captured under the {@code
   * http.response.header.<key>} attribute key. The {@code <key>} part in the attribute key is the
   * lowercase header name.
   *
   * <p>The header names are matched literally, so {@code *} and {@code ?} are not treated as glob
   * patterns.
   *
   * @param responseHeaders A list of HTTP header names.
   * @deprecated Use {@link #setResponseHeaders(IncludeExclude)} instead. To be removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
      Collection<String> responseHeaders) {
    this.capturedResponseHeaders = CapturedHttpHeaders.createExact("response", responseHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes as described in
   * <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#common-attributes">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP response header values will be captured under the {@code
   * http.response.header.<key>} attribute key. The {@code <key>} part in the attribute key is the
   * lowercase header name.
   *
   * <p>The header names are matched literally, so {@code *} and {@code ?} are not treated as glob
   * patterns.
   *
   * @param responseHeaders A list of HTTP header names.
   * @deprecated Use {@link #setResponseHeaders(IncludeExclude)} instead. To be removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
      List<String> responseHeaders) {
    return setCapturedResponseHeaders((Collection<String>) responseHeaders);
  }

  /**
   * Configures the extractor to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this extractor defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>. If an
   * unknown method is encountered, the extractor will use the value {@value HttpConstants#_OTHER}
   * instead of it and put the original value in an extra {@code http.request.method_original}
   * attribute.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   */
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setKnownMethods(
      Collection<String> knownMethods) {
    this.knownMethods = new HashSet<>(knownMethods);
    return this;
  }

  /**
   * Configures the extractor to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this extractor defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>. If an
   * unknown method is encountered, the extractor will use the value {@value HttpConstants#_OTHER}
   * instead of it and put the original value in an extra {@code http.request.method_original}
   * attribute.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   */
  // don't deprecate this since users will get deprecation warning without a clean way to suppress
  // it if they're using Set
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setKnownMethods(
      Set<String> knownMethods) {
    return setKnownMethods((Collection<String>) knownMethods);
  }

  // visible for tests
  @CanIgnoreReturnValue
  HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setResendCountIncrementer(
      ToIntFunction<Context> resendCountIncrementer) {
    this.resendCountIncrementer = resendCountIncrementer;
    return this;
  }

  /**
   * Returns a new {@link HttpClientAttributesExtractor} with the settings of this {@link
   * HttpClientAttributesExtractorBuilder}.
   *
   * @see InstrumenterBuilder#addAttributesExtractor(AttributesExtractor)
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new HttpClientAttributesExtractor<>(this);
  }

  InternalNetworkAttributesExtractor<REQUEST, RESPONSE> buildNetworkExtractor() {
    return new InternalNetworkAttributesExtractor<>(
        httpAttributesGetter,
        // network.{transport,type} are opt-in, network.protocol.* have HTTP-specific logic
        /* captureProtocolAttributes= */ false,
        /* captureLocalSocketAttributes= */ false);
  }

  InternalServerAttributesExtractor<REQUEST> buildServerExtractor() {
    return new InternalServerAttributesExtractor<>(serverAddressAndPortExtractor);
  }
}
