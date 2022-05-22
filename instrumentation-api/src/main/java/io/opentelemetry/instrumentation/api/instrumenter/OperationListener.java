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
 */
public interface OperationListener {

  /**
   * Listener method that is called at the start of an instrumented operation. If any state needs to
   * be kept between the start and end of the processing, e.g., an in-progress span, it should be
   * added to the passed in {@link Context} and returned.
   *
   * @param startNanos The nanosecond timestamp marking the start of the operation. Can be used to
   *     compute the duration of the entire operation.
   */
  Context onStart(Context context, Attributes startAttributes, long startNanos);

  /**
   * Listener method that is called at the end of an instrumented operation.
   *
   * @param endNanos The nanosecond timestamp marking the end of the operation. Can be used to
   *     compute the duration of the entire operation.
   */
  void onEnd(Context context, Attributes endAttributes, long endNanos);
}
