/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;

import javax.annotation.Nullable;

// https://github.com/open-telemetry/semantic-conventions/blob/v1.21.0/docs/http/http-spans.md#status
enum HttpStatusCodeConverter {
  SERVER {
    @Override
    boolean isError(int responseStatusCode) {
      return responseStatusCode >= 500
          ||
          // invalid status code, does not exist
          responseStatusCode < 100;
    }
  },
  CLIENT {
    @Override
    boolean isError(int responseStatusCode) {
      return responseStatusCode >= 400
          ||
          // invalid status code, does not exist
          responseStatusCode < 100;
    }
  };

  abstract boolean isError(int responseStatusCode);

  @Nullable
  <REQUEST, RESPONSE> String getErrorType(
      HttpCommonAttributesGetter<REQUEST, RESPONSE> getter,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    Integer statusCode =
        response == null ? null : getter.getHttpResponseStatusCode(request, response, error);
    if (statusCode != null && statusCode > 0 && isError(statusCode)) {
      return statusCode.toString();
    }
    if (error != null || statusCode == null || statusCode <= 0) {
      return defaultErrorType(error);
    }
    return null;
  }

  static String defaultErrorType(@Nullable Throwable error) {
    return error == null ? _OTHER : error.getClass().getName();
  }
}
