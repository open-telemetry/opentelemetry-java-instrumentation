/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.decorator;

import io.opentelemetry.trace.Status;

public final class HttpStatusConverter {

  // https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#status
  public static Status statusFromHttpStatus(int httpStatus) {
    if (httpStatus >= 100 && httpStatus < 400) {
      return Status.UNSET;
    }

    return Status.ERROR;
  }

  private HttpStatusConverter() {}
}
