/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;

// https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#status
public final class HttpStatusConverter {

  public static StatusCode statusFromHttpStatus(int httpStatus, SpanKind kind) {
    return (kind == SpanKind.SERVER)
        ? serverStatusFromHttpStatus(httpStatus)
        : clientStatusFromHttpStatus(httpStatus);
  }

  private static StatusCode statusFromHttpStatus(int httpStatus, int minError) {
    if (httpStatus >= 100 && httpStatus < minError) {
      return StatusCode.UNSET;
    }

    return StatusCode.ERROR;
  }

  public static StatusCode clientStatusFromHttpStatus(int httpStatus) {
    return statusFromHttpStatus(httpStatus, 400);
  }

  public static StatusCode serverStatusFromHttpStatus(int httpStatus) {
    return statusFromHttpStatus(httpStatus, 500);
  }

  private HttpStatusConverter() {}
}
