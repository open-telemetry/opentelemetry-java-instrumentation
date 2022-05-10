/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

/**
 * Extractor of the root cause of a {@link Throwable}. When instrumenting a library which wraps user
 * exceptions with a framework exception, generally for propagating checked exceptions across
 * unchecked boundaries, it is recommended to override this to unwrap back to the user exception.
 */
@FunctionalInterface
public interface ErrorCauseExtractor {

  /** Strips away the wrapper exceptions and returns the actual cause of a request error. */
  Throwable extract(Throwable error);

  /**
   * Returns a {@link ErrorCauseExtractor} which unwraps common standard library wrapping
   * exceptions.
   */
  static ErrorCauseExtractor jdk() {
    return JdkErrorCauseExtractor.INSTANCE;
  }
}
