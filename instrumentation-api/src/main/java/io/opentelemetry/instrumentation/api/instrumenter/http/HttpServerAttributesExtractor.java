/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-server">HTTP
 * server attributes</a>. Instrumentation of HTTP server frameworks should extend this class,
 * defining {@link REQUEST} and {@link RESPONSE} with the actual request / response types of the
 * instrumented library. If an attribute is not available in this library, it is appropriate to
 * return {@code null} from the protected attribute methods, but implement as many as possible for
 * best compliance with the OpenTelemetry specification.
 */
public abstract class HttpServerAttributesExtractor<REQUEST, RESPONSE>
    extends HttpCommonAttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Creates the HTTP server attributes extractor.
   *
   * @param capturedHttpHeaders A configuration object specifying which HTTP request and response
   *     headers should be captured as span attributes.
   */
  protected HttpServerAttributesExtractor(CapturedHttpHeaders capturedHttpHeaders) {
    super(capturedHttpHeaders);
  }

  /** Creates the HTTP server attributes extractor with default configuration. */
  protected HttpServerAttributesExtractor() {
    this(CapturedHttpHeaders.server(Config.get()));
  }

  @Override
  protected final void onStart(AttributesBuilder attributes, REQUEST request) {
    super.onStart(attributes, request);

    set(attributes, SemanticAttributes.HTTP_FLAVOR, flavor(request));
    set(attributes, SemanticAttributes.HTTP_SCHEME, scheme(request));
    set(attributes, SemanticAttributes.HTTP_HOST, host(request));
    set(attributes, SemanticAttributes.HTTP_TARGET, target(request));
    set(attributes, SemanticAttributes.HTTP_ROUTE, route(request));
  }

  @Override
  protected final void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    super.onEnd(attributes, request, response, error);
    set(attributes, SemanticAttributes.HTTP_SERVER_NAME, serverName(request, response));
  }

  // Attributes that always exist in a request

  @Nullable
  protected abstract String flavor(REQUEST request);

  @Nullable
  protected abstract String target(REQUEST request);

  // TODO: remove implementations?
  @Nullable
  protected String host(REQUEST request) {
    List<String> values = requestHeader(request, "host");
    return values.isEmpty() ? null : values.get(0);
  }

  @Nullable
  protected abstract String route(REQUEST request);

  @Nullable
  protected abstract String scheme(REQUEST request);

  // Attributes which are not always available when the request is ready.

  /**
   * Extracts the {@code http.server_name} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  protected abstract String serverName(REQUEST request, @Nullable RESPONSE response);
}
