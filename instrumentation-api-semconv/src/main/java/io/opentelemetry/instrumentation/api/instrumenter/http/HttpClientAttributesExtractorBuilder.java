/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InternalNetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/** A builder of {@link HttpClientAttributesExtractor}. */
public final class HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter;

  @SuppressWarnings("deprecation") // using the net extractor for the old->stable semconv story
  final io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
          REQUEST, RESPONSE>
      netAttributesGetter;

  final HttpAddressPortExtractor<REQUEST> addressPortExtractor;
  List<String> capturedRequestHeaders = emptyList();
  List<String> capturedResponseHeaders = emptyList();
  Set<String> knownMethods = HttpConstants.KNOWN_METHODS;
  ToIntFunction<Context> resendCountIncrementer = HttpClientResendCount::getAndIncrement;

  HttpClientAttributesExtractorBuilder(
      HttpClientAttributesGetter<REQUEST, RESPONSE> httpAttributesGetter,
      @SuppressWarnings("deprecation") // using the net extractor for the old->stable semconv story
          io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
                  REQUEST, RESPONSE>
              netAttributesGetter) {
    this.httpAttributesGetter = httpAttributesGetter;
    this.netAttributesGetter = netAttributesGetter;
    addressPortExtractor = new HttpAddressPortExtractor<>(httpAttributesGetter);
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes as described in <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-spans.md#common-attributes">HTTP
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
    this.capturedRequestHeaders = new ArrayList<>(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes as described in
   * <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-spans.md#common-attributes">HTTP
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
    this.capturedResponseHeaders = new ArrayList<>(responseHeaders);
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
  @CanIgnoreReturnValue
  public HttpClientAttributesExtractorBuilder<REQUEST, RESPONSE> setKnownMethods(
      Set<String> knownMethods) {
    this.knownMethods = new HashSet<>(knownMethods);
    return this;
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
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new HttpClientAttributesExtractor<>(this);
  }

  InternalNetClientAttributesExtractor<REQUEST, RESPONSE> buildNetExtractor() {
    return new InternalNetClientAttributesExtractor<>(
        netAttributesGetter, addressPortExtractor, SemconvStability.emitOldHttpSemconv());
  }

  InternalNetworkAttributesExtractor<REQUEST, RESPONSE> buildNetworkExtractor() {
    return new InternalNetworkAttributesExtractor<>(
        netAttributesGetter,
        HttpNetworkTransportFilter.INSTANCE,
        SemconvStability.emitStableHttpSemconv(),
        SemconvStability.emitOldHttpSemconv());
  }

  InternalServerAttributesExtractor<REQUEST, RESPONSE> buildServerExtractor() {
    return new InternalServerAttributesExtractor<>(
        netAttributesGetter,
        new ClientSideServerPortCondition<>(httpAttributesGetter),
        addressPortExtractor,
        SemconvStability.emitStableHttpSemconv(),
        SemconvStability.emitOldHttpSemconv(),
        InternalServerAttributesExtractor.Mode.PEER,
        /* captureServerSocketAttributes= */ true);
  }
}
