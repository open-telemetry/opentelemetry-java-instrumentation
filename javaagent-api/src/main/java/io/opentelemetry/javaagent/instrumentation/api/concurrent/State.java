/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.concurrent;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class State {

  private static final Logger log = LoggerFactory.getLogger(State.class);

  private static final AtomicReferenceFieldUpdater<State, Context> parentContextUpdater =
      AtomicReferenceFieldUpdater.newUpdater(State.class, Context.class, "parentContext");

  public static final ContextStore.Factory<State> FACTORY = State::new;

  private volatile Context parentContext;

  private State() {}

  public void setParentContext(Context parentContext) {
    boolean result = parentContextUpdater.compareAndSet(this, null, parentContext);
    if (!result) {
      Context currentParent = parentContextUpdater.get(this);
      if (currentParent != parentContext) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Failed to set parent context because another parent context is "
                  + "already set {}: new: {}, old: {}",
              this,
              parentContext,
              currentParent);
        }
      }
    }
  }

  public void clearParentContext() {
    parentContextUpdater.set(this, null);
  }

  public Context getAndResetParentContext() {
    return parentContextUpdater.getAndSet(this, null);
  }
}
