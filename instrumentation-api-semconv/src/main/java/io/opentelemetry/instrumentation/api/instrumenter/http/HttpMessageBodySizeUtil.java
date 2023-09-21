/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

final class HttpMessageBodySizeUtil {

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  private static final AttributeKey<Long> HTTP_REQUEST_BODY_SIZE =
      SemconvStability.emitOldHttpSemconv()
          ? SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH
          : SemanticAttributes.HTTP_REQUEST_BODY_SIZE;

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  private static final AttributeKey<Long> HTTP_RESPONSE_BODY_SIZE =
      SemconvStability.emitOldHttpSemconv()
          ? SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH
          : SemanticAttributes.HTTP_RESPONSE_BODY_SIZE;

  @Nullable
  static Long getHttpRequestBodySize(Attributes... attributesList) {
    return getAttribute(HTTP_REQUEST_BODY_SIZE, attributesList);
  }

  @Nullable
  static Long getHttpResponseBodySize(Attributes... attributesList) {
    return getAttribute(HTTP_RESPONSE_BODY_SIZE, attributesList);
  }

  @Nullable
  private static <T> T getAttribute(AttributeKey<T> key, Attributes... attributesList) {
    for (Attributes attributes : attributesList) {
      T value = attributes.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private HttpMessageBodySizeUtil() {}
}
