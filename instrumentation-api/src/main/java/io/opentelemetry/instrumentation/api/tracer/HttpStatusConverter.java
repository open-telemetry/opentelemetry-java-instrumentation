/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.StatusCode;

public interface HttpStatusConverter {

  HttpStatusConverter SERVER = HttpServerStatusConverter.INSTANCE;
  HttpStatusConverter CLIENT = HttpClientStatusConverter.INSTANCE;

  StatusCode statusFromHttpStatus(int httpStatus);
}
