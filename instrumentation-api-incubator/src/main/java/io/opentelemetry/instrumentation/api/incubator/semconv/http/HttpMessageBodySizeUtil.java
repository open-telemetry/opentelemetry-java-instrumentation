/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.SemanticAttributes;
import javax.annotation.Nullable;

final class HttpMessageBodySizeUtil {
  // FIXME JBLEY can be inlined and removed I think

  @Nullable
  static Long getHttpRequestBodySize(Attributes... attributesList) {
    return getAttribute(SemanticAttributes.HTTP_REQUEST_BODY_SIZE, attributesList);
  }

  @Nullable
  static Long getHttpResponseBodySize(Attributes... attributesList) {
    return getAttribute(SemanticAttributes.HTTP_RESPONSE_BODY_SIZE, attributesList);
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
