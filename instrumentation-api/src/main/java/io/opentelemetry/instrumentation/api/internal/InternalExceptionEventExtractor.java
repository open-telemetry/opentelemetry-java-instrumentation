/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.context.Context;

/**
 * Internal functional interface for exception event extraction. Public API is in {@code
 * ExceptionEventExtractor} in the incubator module.
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
