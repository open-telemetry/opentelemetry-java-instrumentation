/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import javax.annotation.Nullable;

/**
 * Holds the scope of the stashed message that is currently being unstashed. A message is
 * interpreted right after it is taken from the stash, so its scope stays open until the next
 * message is taken from the stash or until unstashing ends.
 */
public final class UnstashScope {

  @Nullable private Scope scope;

  public void switchTo(@Nullable Context context) {
    close();
    // a message stashed without a context was sent in the root context, make root current so that
    // it doesn't run in the context of the message that triggered the unstash
    scope = (context != null ? context : Context.root()).makeCurrent();
  }

  public void close() {
    if (scope != null) {
      scope.close();
      scope = null;
    }
  }
}
