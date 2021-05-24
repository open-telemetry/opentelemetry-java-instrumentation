/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;

final class GrpcErrorCauseExtractor implements ErrorCauseExtractor {
  @Override
  public Throwable extractCause(Throwable error) {
    if (error.getCause() == null) {
      return error;
    }
    if (error instanceof StatusException || error instanceof StatusRuntimeException) {
      return extractCause(error.getCause());
    }
    return ErrorCauseExtractor.jdk().extractCause(error);
  }
}
