package io.opentelemetry.helpers.core;

import io.opentelemetry.trace.Status;

/**
 * Default implementation of {@link StatusTranslator} which returns either OK or INTERNAL status.
 *
 * @param <P> the response or output object type
 */
public class DefaultStatusTranslator<P> implements StatusTranslator<P> {

  @Override
  public Status calculateStatus(Throwable throwable, P response) {
    return throwable == null ? Status.OK : Status.INTERNAL;
  }
}
