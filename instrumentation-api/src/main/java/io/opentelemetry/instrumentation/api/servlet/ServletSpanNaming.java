/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helper container for tracking whether servlet integration should update server span name or not.
 */
public class ServletSpanNaming {

  private static final ContextKey<ServletSpanNaming> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-span-naming-key");

  public static Context init(Context ctx) {
    if (ctx.get(CONTEXT_KEY) != null) {
      return ctx;
    }
    return ctx.with(CONTEXT_KEY, new ServletSpanNaming());
  }

  private final AtomicBoolean servletUpdatedServerSpanName = new AtomicBoolean(false);

  private ServletSpanNaming() {}

  /**
   * Returns true, if servlet integration should update server span name. After server span name has
   * been updated with <code>setServletUpdatedServerSpanName</code> this method will return <code>
   * false</code>.
   *
   * @param ctx server context
   * @return <code>true</code>, if the server span name should be updated by servlet integration, or
   *     <code>false</code> otherwise.
   */
  public static boolean shouldUpdateServerSpanName(Context ctx) {
    ServletSpanNaming servletSpanNaming = ctx.get(CONTEXT_KEY);
    if (servletSpanNaming != null) {
      return !servletSpanNaming.servletUpdatedServerSpanName.get();
    }
    return false;
  }

  /**
   * Indicate that the servlet integration has updated the name for the server span.
   *
   * @param ctx server context
   */
  public static void setServletUpdatedServerSpanName(Context ctx, boolean value) {
    ServletSpanNaming servletSpanNaming = ctx.get(CONTEXT_KEY);
    if (servletSpanNaming != null) {
      servletSpanNaming.servletUpdatedServerSpanName.set(value);
    }
  }
}
