/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

// https://github.com/open-telemetry/semantic-conventions/blob/v1.21.0/docs/http/http-spans.md#status
enum HttpStatusCodeConverter {
  SERVER {
    @Override
    boolean isError(int responseStatusCode) {
      return responseStatusCode >= 500
          ||
          // invalid status code, does not exists
          responseStatusCode < 100;
    }
  },
  CLIENT {
    @Override
    boolean isError(int responseStatusCode) {
      return responseStatusCode >= 400
          ||
          // invalid status code, does not exists
          responseStatusCode < 100;
    }
  };

  abstract boolean isError(int responseStatusCode);
}
