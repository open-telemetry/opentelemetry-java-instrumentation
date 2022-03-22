/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.logging.Logger;

/** Represents a {@link Context} attached to a concurrent task instance. */
public final class PropagatedContext {

  private static final Logger logger = Logger.getLogger(PropagatedContext.class.getName());

  private static final AtomicReferenceFieldUpdater<PropagatedContext, Context> contextUpdater =
      AtomicReferenceFieldUpdater.newUpdater(PropagatedContext.class, Context.class, "context");

  // Used by AtomicReferenceFieldUpdater
  @SuppressWarnings("UnusedVariable")
  private volatile Context context;

  PropagatedContext() {}

  void setContext(Context context) {
    boolean result = contextUpdater.compareAndSet(this, null, context);
    if (!result) {
      Context currentPropagatedContext = contextUpdater.get(this);
      if (currentPropagatedContext != context) {
        logger.log(
            FINE,
            "Failed to propagate context because previous propagated context is already set; new: {0}, old: {1}",
            new Object[] {context, currentPropagatedContext});
      }
    }
  }

  void clear() {
    contextUpdater.set(this, null);
  }

  Context getAndClear() {
    return contextUpdater.getAndSet(this, null);
  }

  Context get() {
    return contextUpdater.get(this);
  }
}
