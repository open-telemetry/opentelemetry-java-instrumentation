/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quarkus.resteasy.reactive.v1_11;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import javax.annotation.Nullable;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;

public class OtelRequestContext {
  private static final ScopedThreadValue<OtelRequestContext> contextThreadLocal =
      new ScopedThreadValue<>();
  @Nullable private OtelRequestContext previous;
  private boolean firstInvoke = true;

  public static OtelRequestContext start(ResteasyReactiveRequestContext requestContext) {
    ResteasyReactiveSpanName.updateServerSpanName(requestContext);
    OtelRequestContext context = new OtelRequestContext();
    context.previous = contextThreadLocal.set(context);
    return context;
  }

  public static void onInvoke(ResteasyReactiveRequestContext requestContext) {
    OtelRequestContext context = contextThreadLocal.get();
    if (context == null) {
      return;
    }
    // we ignore the first invoke as it uses the same context that we get in start, the second etc.
    // invoke will be for sub resource locator that changes the path
    if (context.firstInvoke) {
      context.firstInvoke = false;
      return;
    }
    ResteasyReactiveSpanName.updateServerSpanName(requestContext);
  }

  public void close() {
    contextThreadLocal.restore(previous);
  }

  private OtelRequestContext() {}
}
