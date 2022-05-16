/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.StatusCode;

/** A builder that exposes methods for setting the status of a span. */
public interface SpanStatusBuilder {

  /**
   * Sets the status to the {@code Span}.
   *
   * <p>If used, this will override the default {@code Span} status. Default status code is {@link
   * StatusCode#UNSET}.
   *
   * <p>Only the value of the last call will be recorded, and implementations are free to ignore
   * previous calls.
   *
   * @param statusCode the {@link StatusCode} to set.
   * @return this.
   * @see Span#setStatus(StatusCode)
   */
  default SpanStatusBuilder setStatus(StatusCode statusCode) {
    return setStatus(statusCode, "");
  }

  /**
   * Sets the status to the {@code Span}.
   *
   * <p>If used, this will override the default {@code Span} status. Default status code is {@link
   * StatusCode#UNSET}.
   *
   * <p>Only the value of the last call will be recorded, and implementations are free to ignore
   * previous calls.
   *
   * @param statusCode the {@link StatusCode} to set.
   * @param description the description of the {@code Status}.
   * @return this.
   * @see io.opentelemetry.api.trace.Span#setStatus(StatusCode,String)
   */
  SpanStatusBuilder setStatus(StatusCode statusCode, String description);
}
