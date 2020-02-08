package io.opentelemetry.helpers.core;

import io.opentelemetry.trace.Status;

/**
 * Functional interface defining translation of native status values of the decorated method into
 * the OpenTelemetry equivalent.
 *
 * @param <P> the response or output object type
 */
public interface StatusTranslator<P> {

  /**
   * Returns the equivalent OpenTelemetry status.
   *
   * @param throwable the cause of the span failure or null if none
   * @param response the response if there is one
   * @return the OpenTelemetry status
   */
  Status calculateStatus(Throwable throwable, P response);
}
