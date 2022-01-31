/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwarderHeaderParser.extractForwarded;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwarderHeaderParser.extractForwardedFor;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-server">HTTP
 * server attributes</a>. Instrumentation of HTTP server frameworks should extend this class,
 * defining {@link REQUEST} and {@link RESPONSE} with the actual request / response types of the
 * instrumented library. If an attribute is not available in this library, it is appropriate to
 * return {@code null} from the protected attribute methods, but implement as many as possible for
 * best compliance with the OpenTelemetry specification.
 */
public final class HttpServerAttributesExtractor<REQUEST, RESPONSE>
    extends HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, HttpServerAttributesGetter<REQUEST, RESPONSE>> {

  /** Creates the HTTP server attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return create(getter, CapturedHttpHeaders.server(Config.get()));
  }

  // TODO: there should be a builder for all optional attributes
  /**
   * Creates the HTTP server attributes extractor.
   *
   * @param capturedHttpHeaders A configuration object specifying which HTTP request and response
   *     headers should be captured as span attributes.
   */
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter,
      CapturedHttpHeaders capturedHttpHeaders) {
    return new HttpServerAttributesExtractor<>(getter, capturedHttpHeaders);
  }

  private HttpServerAttributesExtractor(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter,
      CapturedHttpHeaders capturedHttpHeaders) {
    super(getter, capturedHttpHeaders);
  }

  @Override
  public void onStart(AttributesBuilder attributes, REQUEST request) {
    super.onStart(attributes, request);

    set(attributes, SemanticAttributes.HTTP_FLAVOR, getter.flavor(request));
    set(attributes, SemanticAttributes.HTTP_SCHEME, getter.scheme(request));
    set(attributes, SemanticAttributes.HTTP_HOST, host(request));
    set(attributes, SemanticAttributes.HTTP_TARGET, getter.target(request));
    set(attributes, SemanticAttributes.HTTP_ROUTE, getter.route(request));
    set(attributes, SemanticAttributes.HTTP_CLIENT_IP, clientIp(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    super.onEnd(attributes, request, response, error);
    set(attributes, SemanticAttributes.HTTP_SERVER_NAME, getter.serverName(request, response));
  }

  @Nullable
  private String host(REQUEST request) {
    return firstHeaderValue(getter.requestHeader(request, "host"));
  }

  @Nullable
  private String clientIp(REQUEST request) {
    // try Forwarded
    String forwarded = firstHeaderValue(getter.requestHeader(request, "forwarded"));
    if (forwarded != null) {
      forwarded = extractForwarded(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    // try X-Forwarded-For
    forwarded = firstHeaderValue(getter.requestHeader(request, "x-forwarded-for"));
    if (forwarded != null) {
      return extractForwardedFor(forwarded);
    }

    return null;
  }
}
