/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import javax.annotation.Nullable;

final class HttpMessageBodySizeUtil {
  @Nullable
  static Long getHttpRequestBodySize(Attributes attributes) {
    return attributes.get(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE);
  }

  @Nullable
  static Long getHttpResponseBodySize(Attributes attributes) {
    return attributes.get(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE);
  }

  private HttpMessageBodySizeUtil() {}
}
