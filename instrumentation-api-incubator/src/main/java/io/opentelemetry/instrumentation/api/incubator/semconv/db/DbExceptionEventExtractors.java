/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.ExceptionEventExtractor;

/**
 * {@link ExceptionEventExtractor} constants for DB client instrumentations.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DbExceptionEventExtractors {

  /** Exception event extractor for DB client spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> client() {
    return ExceptionEventExtractor.create("db.client.operation.exception", Severity.WARN);
  }

  private DbExceptionEventExtractors() {}
}
