/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.trace.StatusCode;

final class HttpServerStatusConverter implements HttpStatusConverter {

  static final HttpStatusConverter INSTANCE = new HttpServerStatusConverter();

  // https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#status
  @Override
  public StatusCode statusFromHttpStatus(int httpStatus) {
    if (httpStatus >= 100 && httpStatus < 500) {
      return StatusCode.UNSET;
    }

    return StatusCode.ERROR;
  }

  private HttpServerStatusConverter() {}
}
