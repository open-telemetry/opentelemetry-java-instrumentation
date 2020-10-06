/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.decorator;

import io.opentelemetry.trace.StatusCanonicalCode;

public final class HttpStatusConverter {

  // https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#status
  public static StatusCanonicalCode statusFromHttpStatus(int httpStatus) {
    if (httpStatus >= 100 && httpStatus < 400) {
      return StatusCanonicalCode.UNSET;
    }

    return StatusCanonicalCode.ERROR;
  }

  private HttpStatusConverter() {}
}
