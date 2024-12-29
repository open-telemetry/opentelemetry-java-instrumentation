/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.semconv.network.internal.AddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.ClientAddressAndPortExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.InternalClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.url.internal.InternalUrlAttributesExtractor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A builder of {@link HttpServerAttributesExtractor}.
 *
 * @since 2.0.0
 */
public final class HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter;

  final AddressAndPortExtractor<REQUEST> clientAddressPortExtractor;
  final AddressAndPortExtractor<REQUEST> serverAddressPortExtractor;
  List<String> capturedRequestHeaders = emptyList();
  List<String> capturedResponseHeaders = emptyList();
  Set<String> knownMethods = HttpConstants.KNOWN_METHODS;
  Function<Context, String> httpRouteGetter = HttpServerRoute::get;

  HttpServerAttributesExtractorBuilder(
      HttpServerAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter) {
    this.httpAttributesGetter = httpAttributesGetter;

    clientAddressPortExtractor =
        new ClientAddressAndPortExtractor<>(
            httpAttributesGetter, new HttpServerAddressAndPortExtractor<>(httpAttributesGetter));
    serverAddressPortExtractor = new ForwardedHostAddressAndPortExtractor<>(httpAttributesGetter);
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes as described in
   * <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#http-server-semantic-conventions">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP request header values will be captured under the {@code http.request.header.<key>}
   * attribute key. The {@code <key>} part in the attribute key is the lowercase header name.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
      Collection<String> requestHeaders) {
    this.capturedRequestHeaders = new ArrayList<>(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes as described in
   * <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-spans.md#http-server-semantic-conventions">HTTP
   * semantic conventions</a>.
   *
   * <p>The HTTP request header values will be captured under the {@code http.request.header.<key>}
   * attribute key. The {@code <key>} part in the attribute key is the lowercase header name.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  // don't deprecate this since users will get deprecation warning without a clean way to suppress
  // it if they're using List
  @CanIgnoreReturnValue
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
      List<String> requestHeaders) {
    return setCapturedRequestHeaders((Collection<String>) requestHeaders);
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
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
      Collection<String> responseHeaders) {
    this.capturedResponseHeaders = new ArrayList<>(responseHeaders);
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
   * @param responseHeaders A list of HTTP header names.
   */
  // don't deprecate this since users will get deprecation warning without a clean way to suppress
  // it if they're using List
  @CanIgnoreReturnValue
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
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
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setKnownMethods(
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
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setKnownMethods(
      Set<String> knownMethods) {
    return setKnownMethods((Collection<String>) knownMethods);
  }

  // visible for tests
  @CanIgnoreReturnValue
  HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setHttpRouteGetter(
      Function<Context, String> httpRouteGetter) {
    this.httpRouteGetter = httpRouteGetter;
    return this;
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractor} with the settings of this {@link
   * HttpServerAttributesExtractorBuilder}.
   *
   * @see InstrumenterBuilder#addAttributesExtractor(AttributesExtractor)
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new HttpServerAttributesExtractor<>(this);
  }

  InternalUrlAttributesExtractor<REQUEST> buildUrlExtractor() {
    return new InternalUrlAttributesExtractor<>(
        httpAttributesGetter, new ForwardedUrlSchemeProvider<>(httpAttributesGetter));
  }

  InternalNetworkAttributesExtractor<REQUEST, RESPONSE> buildNetworkExtractor() {
    return new InternalNetworkAttributesExtractor<>(
        httpAttributesGetter,
        // network.{transport,type} are opt-in, network.protocol.* have HTTP-specific logic
        /* captureProtocolAttributes= */ false,
        // network.local.* are opt-in
        /* captureLocalSocketAttributes= */ false);
  }

  InternalServerAttributesExtractor<REQUEST> buildServerExtractor() {
    return new InternalServerAttributesExtractor<>(serverAddressPortExtractor);
  }

  InternalClientAttributesExtractor<REQUEST> buildClientExtractor() {
    return new InternalClientAttributesExtractor<>(
        clientAddressPortExtractor,
        // client.port is opt-in
        /* capturePort= */ false);
  }
}
