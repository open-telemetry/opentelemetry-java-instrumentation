/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/**
 * A listener of the start and end of an instrumented operation. The {@link #onStart(Context,
 * Attributes, long)} methods will be called as early as possible in the processing of a request;
 * and the {@link #onEnd(Context, Attributes, long)} method will be called as late as possible when
 * finishing the processing of a response. These correspond to the start and end of a span when
 * tracing.
 *
 * @deprecated Use {@link OperationListener} instead.
 */
@Deprecated
public interface RequestListener extends OperationListener {

  /**
   * Listener method that is called at the start of a request. If any state needs to be kept between
   * the start and end of the request, e.g., an in-progress span, it should be added to the passed
   * in {@link Context} and returned.
   *
   * @param startNanos The nanosecond timestamp marking the start of the request. Can be used to
   *     compute the duration of the entire operation.
   * @deprecated Implement the {@link #onStart(Context, Attributes, long)} method instead.
   */
  @Deprecated
  default Context start(Context context, Attributes startAttributes, long startNanos) {
    throw new UnsupportedOperationException(
        "This method variant is deprecated and will be removed in the next minor release.");
  }

  @Override
  default Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return start(context, startAttributes, startNanos);
  }

  /**
   * Listener method that is called at the end of a request.
   *
   * @param endNanos The nanosecond timestamp marking the end of the request. Can be used to compute
   *     the duration of the entire operation.
   * @deprecated Implement the {@link #onEnd(Context, Attributes, long)} method instead.
   */
  @Deprecated
  default void end(Context context, Attributes endAttributes, long endNanos) {
    throw new UnsupportedOperationException(
        "This method variant is deprecated and will be removed in the next minor release.");
  }

  @Override
  default void onEnd(Context context, Attributes endAttributes, long endNanos) {
    end(context, endAttributes, endNanos);
  }
}
