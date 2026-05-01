/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.ExceptionEventExtractor;

/**
 * {@link ExceptionEventExtractor} constants for messaging instrumentations.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingExceptionEventExtractors {

  /** Exception event extractor for messaging create operation spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> create() {
    return ExceptionEventExtractor.create("messaging.create.exception", Severity.WARN);
  }

  /** Exception event extractor for messaging send operation spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> send() {
    return ExceptionEventExtractor.create("messaging.send.exception", Severity.WARN);
  }

  /** Exception event extractor for messaging receive operation spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> receive() {
    return ExceptionEventExtractor.create("messaging.receive.exception", Severity.WARN);
  }

  /** Exception event extractor for messaging settle operation spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> settle() {
    return ExceptionEventExtractor.create("messaging.settle.exception", Severity.WARN);
  }

  /** Exception event extractor for messaging process spans. */
  public static <REQUEST> ExceptionEventExtractor<REQUEST> process() {
    return ExceptionEventExtractor.create("messaging.process.exception", Severity.ERROR);
  }

  private MessagingExceptionEventExtractors() {}
}
