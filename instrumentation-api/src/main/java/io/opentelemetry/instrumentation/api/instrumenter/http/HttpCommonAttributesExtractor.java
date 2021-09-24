/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#common-attributes">HTTP
 * attributes</a> that are common to client and server instrumentations.
 */
public abstract class HttpCommonAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<REQUEST, RESPONSE> {

  // directly extending this class should not be possible outside of this package
  HttpCommonAttributesExtractor() {}

  @Override
  protected void onStart(AttributesBuilder attributes, REQUEST request) {
    set(attributes, SemanticAttributes.HTTP_METHOD, method(request));
    set(attributes, SemanticAttributes.HTTP_USER_AGENT, userAgent(request));
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    set(
        attributes,
        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
        requestContentLength(request, response));
    set(
        attributes,
        SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH_UNCOMPRESSED,
        requestContentLengthUncompressed(request, response));
    set(attributes, SemanticAttributes.HTTP_FLAVOR, flavor(request, response));
    if (response != null) {
      Integer statusCode = statusCode(request, response);
      if (statusCode != null) {
        set(attributes, SemanticAttributes.HTTP_STATUS_CODE, (long) statusCode);
      }
      set(
          attributes,
          SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
          responseContentLength(request, response));
      set(
          attributes,
          SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH_UNCOMPRESSED,
          responseContentLengthUncompressed(request, response));
    }
  }

  // Attributes that always exist in a request

  @Nullable
  protected abstract String method(REQUEST request);

  @Nullable
  protected abstract String userAgent(REQUEST request);

  // Attributes which are not always available when the request is ready.

  /**
   * Extracts the {@code http.request_content_length} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  protected abstract Long requestContentLength(REQUEST request, @Nullable RESPONSE response);

  /**
   * Extracts the {@code http.request_content_length_uncompressed} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  protected abstract Long requestContentLengthUncompressed(
      REQUEST request, @Nullable RESPONSE response);

  /**
   * Extracts the {@code http.flavor} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, whether
   * {@code response} is {@code null} or not.
   */
  @Nullable
  protected abstract String flavor(REQUEST request, @Nullable RESPONSE response);

  /**
   * Extracts the {@code http.status_code} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   */
  @Nullable
  protected abstract Integer statusCode(REQUEST request, RESPONSE response);

  /**
   * Extracts the {@code http.response_content_length} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   */
  @Nullable
  protected abstract Long responseContentLength(REQUEST request, RESPONSE response);

  /**
   * Extracts the {@code http.response_content_length_uncompressed} span attribute.
   *
   * <p>This is called from {@link Instrumenter#end(Context, Object, Object, Throwable)}, only when
   * {@code response} is non-{@code null}.
   */
  @Nullable
  protected abstract Long responseContentLengthUncompressed(REQUEST request, RESPONSE response);
}
