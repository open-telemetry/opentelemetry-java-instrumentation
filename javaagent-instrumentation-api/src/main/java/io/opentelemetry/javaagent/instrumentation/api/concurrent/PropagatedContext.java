/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import io.opentelemetry.context.Context;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Represents a {@link Context} attached to a concurrent task instance. */
public final class PropagatedContext {

  private static final Logger logger = LoggerFactory.getLogger(PropagatedContext.class);

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
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Failed to propagate context because previous propagated context is "
                  + "already set {}: new: {}, old: {}",
              this,
              context,
              currentPropagatedContext);
        }
      }
    }
  }

  void clear() {
    contextUpdater.set(this, null);
  }

  Context getAndClear() {
    return contextUpdater.getAndSet(this, null);
  }
}
