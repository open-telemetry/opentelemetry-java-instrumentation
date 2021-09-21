/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/**
 * A listener of the start and end of a request. Instrumented libraries will call {@link
 * #start(Context, Attributes, long)} as early as possible in the processing of a request and {@link
 * #end(Context, Attributes, long)} as late as possible when finishing the request. These correspond
 * to the start and end of a span when tracing.
 */
public interface RequestListener {

  /**
   * Listener method that is called at the start of a request. If any state needs to be kept between
   * the start and end of the request, e.g., an in-progress span, it should be added to the passed
   * in {@link Context} and returned.
   *
   * @param startNanos The nanosecond timestamp marking the start of the request. Can be used to
   *     compute the duration of the entire operation.
   */
  Context start(Context context, Attributes startAttributes, long startNanos);

  /**
   * Listener method that is called at the end of a request.
   *
   * @param endNanos The nanosecond timestamp marking the end of the request. Can be used to compute
   *     the duration of the entire operation.
   */
  void end(Context context, Attributes endAttributes, long endNanos);
}
