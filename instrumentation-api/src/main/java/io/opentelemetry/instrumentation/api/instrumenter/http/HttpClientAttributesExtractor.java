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
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-client">HTTP
 * client attributes</a>. Instrumentation of HTTP client frameworks should extend this class,
 * defining {@link REQUEST} and {@link RESPONSE} with the actual request / response types of the
 * instrumented library. If an attribute is not available in this library, it is appropriate to
 * return {@code null} from the protected attribute methods, but implement as many as possible for
 * best compliance with the OpenTelemetry specification.
 */
public abstract class HttpClientAttributesExtractor<REQUEST, RESPONSE>
    extends HttpCommonAttributesExtractor<REQUEST, RESPONSE> {

  /**
   * Creates the HTTP client attributes extractor.
   *
   * @param capturedHttpHeaders A configuration object specifying which HTTP request and response
   *     headers should be captured as span attributes.
   */
  protected HttpClientAttributesExtractor(CapturedHttpHeaders capturedHttpHeaders) {
    super(capturedHttpHeaders);
  }

  /** Creates the HTTP client attributes extractor with default configuration. */
  protected HttpClientAttributesExtractor() {
    this(CapturedHttpHeaders.client(Config.get()));
  }

  @Override
  public final void onStart(AttributesBuilder attributes, REQUEST request) {
    super.onStart(attributes, request);
    set(attributes, SemanticAttributes.HTTP_URL, url(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    super.onEnd(attributes, request, response, error);
    set(attributes, SemanticAttributes.HTTP_FLAVOR, flavor(request, response));
  }

  // Attributes that always exist in a request

  @Nullable
  protected abstract String url(REQUEST request);

  // Attributes which are not always available when the request is ready.

  /**
   * Extracts the {@code http.flavor} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  protected abstract String flavor(REQUEST request, @Nullable RESPONSE response);
}
