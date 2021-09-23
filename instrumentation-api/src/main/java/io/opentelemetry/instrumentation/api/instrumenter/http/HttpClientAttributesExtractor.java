/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributesBuilder;
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

  @Override
  protected final void onStart(AttributesBuilder attributes, REQUEST request) {
    super.onStart(attributes, request);
    set(attributes, SemanticAttributes.HTTP_URL, url(request));

    // TODO: these are specific to servers, should we remove those?
    set(attributes, SemanticAttributes.HTTP_TARGET, target(request));
    set(attributes, SemanticAttributes.HTTP_HOST, host(request));
    set(attributes, SemanticAttributes.HTTP_SCHEME, scheme(request));
  }

  @Override
  protected final void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    super.onEnd(attributes, request, response, error);
  }

  // Attributes that always exist in a request

  @Nullable
  protected abstract String url(REQUEST request);

  // TODO: this is specific to servers, should we remove this?
  @Nullable
  protected abstract String target(REQUEST request);

  // TODO: this is specific to servers, should we remove this?
  @Nullable
  protected abstract String host(REQUEST request);

  // TODO: this is specific to servers, should we remove this?
  @Nullable
  protected abstract String scheme(REQUEST request);
}
