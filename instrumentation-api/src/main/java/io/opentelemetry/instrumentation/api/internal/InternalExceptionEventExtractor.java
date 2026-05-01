/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;

/**
 * Internal functional interface for exception event extraction.
 *
 * <p>This is temporary bridge API while exception event extraction is not available in the stable
 * instrumentation API artifact. This interface should be revisited when a public API is added.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@FunctionalInterface
public interface InternalExceptionEventExtractor<REQUEST> {

  /**
   * Populates the exception event {@link LogRecordBuilder} with the event name, severity, and any
   * additional attributes for the given context and request.
   */
  void extract(LogRecordBuilder logRecordBuilder, Context context, REQUEST request);
}
