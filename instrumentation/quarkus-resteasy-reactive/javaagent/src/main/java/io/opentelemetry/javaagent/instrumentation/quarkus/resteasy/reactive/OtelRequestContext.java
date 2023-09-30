/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quarkus.resteasy.reactive;

import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;

public final class OtelRequestContext {
  private static final ThreadLocal<OtelRequestContext> contextThreadLocal = new ThreadLocal<>();
  private boolean firstInvoke = true;

  public static OtelRequestContext start(ResteasyReactiveRequestContext requestContext) {
    OtelRequestContext context = new OtelRequestContext();
    contextThreadLocal.set(context);
    ResteasyReactiveSpanName.INSTANCE.updateServerSpanName(requestContext);
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
    ResteasyReactiveSpanName.INSTANCE.updateServerSpanName(requestContext);
  }

  public void close() {
    contextThreadLocal.remove();
  }

  private OtelRequestContext() {}
}
