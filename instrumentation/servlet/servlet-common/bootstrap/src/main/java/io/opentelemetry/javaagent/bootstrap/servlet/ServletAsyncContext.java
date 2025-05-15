/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

public class ServletAsyncContext implements ImplicitContextKeyed {
  private static final ContextKey<ServletAsyncContext> CONTEXT_KEY =
      named("opentelemetry-servlet-async-context");

  private boolean isAsyncListenerAttached;
  private Throwable throwable;
  private Object response;
  private Context context;

  public static Context init(Context context) {
    if (context.get(CONTEXT_KEY) != null) {
      return context;
    }
    return context.with(new ServletAsyncContext());
  }

  @Nullable
  public static ServletAsyncContext get(@Nullable Context context) {
    return context != null ? context.get(CONTEXT_KEY) : null;
  }

  public static boolean isAsyncListenerAttached(@Nullable Context context) {
    ServletAsyncContext servletAsyncContext = get(context);
    return servletAsyncContext != null && servletAsyncContext.isAsyncListenerAttached;
  }

  public static void setAsyncListenerAttached(@Nullable Context context, boolean value) {
    ServletAsyncContext servletAsyncContext = get(context);
    if (servletAsyncContext != null) {
      servletAsyncContext.isAsyncListenerAttached = value;
    }
  }

  public static Throwable getAsyncException(@Nullable Context context) {
    ServletAsyncContext servletAsyncContext = get(context);
    return servletAsyncContext != null ? servletAsyncContext.throwable : null;
  }

  public static void recordAsyncException(@Nullable Context context, Throwable throwable) {
    ServletAsyncContext servletAsyncContext = get(context);
    if (servletAsyncContext != null) {
      servletAsyncContext.throwable = throwable;
    }
  }

  public static Object getAsyncListenerResponse(@Nullable Context context) {
    ServletAsyncContext servletAsyncContext = get(context);
    return servletAsyncContext != null ? servletAsyncContext.response : null;
  }

  public static void setAsyncListenerResponse(Context context, Object response) {
    ServletAsyncContext servletAsyncContext = get(context);
    if (servletAsyncContext != null) {
      servletAsyncContext.response = response;
      servletAsyncContext.context = context;
    }
  }

  public static Context getAsyncListenerContext(Context context) {
    ServletAsyncContext servletAsyncContext = get(context);
    if (servletAsyncContext != null) {
      return servletAsyncContext.context;
    }
    return null;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(CONTEXT_KEY, this);
  }
}
