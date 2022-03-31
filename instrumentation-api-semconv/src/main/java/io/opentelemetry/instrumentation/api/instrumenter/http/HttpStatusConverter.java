/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.trace.StatusCode;

// https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#status
enum HttpStatusConverter {
  SERVER {
    @Override
    StatusCode statusFromHttpStatus(int httpStatus) {
      if (httpStatus >= 100 && httpStatus < 500) {
        return StatusCode.UNSET;
      }

      return StatusCode.ERROR;
    }
  },
  CLIENT {
    @Override
    StatusCode statusFromHttpStatus(int httpStatus) {
      if (httpStatus >= 100 && httpStatus < 400) {
        return StatusCode.UNSET;
      }

      return StatusCode.ERROR;
    }
  };

  abstract StatusCode statusFromHttpStatus(int httpStatus);
}
