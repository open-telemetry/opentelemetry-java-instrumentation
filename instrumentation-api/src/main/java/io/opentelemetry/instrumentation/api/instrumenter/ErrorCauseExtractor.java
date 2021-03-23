/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

public interface ErrorCauseExtractor {
  Throwable extractCause(Throwable error);

  static ErrorCauseExtractor jdk() {
    return JdkErrorCauseExtractor.INSTANCE;
  }
}
