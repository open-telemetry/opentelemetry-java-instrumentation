/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/**
 * A listener of the start and end of a request. Instrumented libraries will call {@link
 * #start(Context, Attributes)} as early as possible in the processing of a request and {@link
 * #end(Context, Attributes)} as late as possible when finishing the request. These correspond to
 * the start and end of a span when tracing.
 */
public interface RequestListener {

  /**
   * Listener method that is called at the start of a request. If any state needs to be kept between
   * the start and end of the request, e.g., an in-progress span, it should be added to the passed
   * in {@link Context} and returned.
   */
  Context start(Context context, Attributes startAttributes);

  /** Listener method that is called at the end of a request. */
  void end(Context context, Attributes endAttributes);
}
