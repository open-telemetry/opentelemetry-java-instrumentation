/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * Helper container for tracking whether servlet integration should update server span name or not.
 */
public abstract class ServletSpanNaming {

  private static final ContextKey<ServletSpanNaming> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-span-naming-key");

  public static Context init(Context context) {
    if (context.get(CONTEXT_KEY) != null) {
      return context;
    }
    return context.with(CONTEXT_KEY, new DefaultServletSpanNaming());
  }

  public static ServletSpanNaming from(Context context) {
    ServletSpanNaming servletSpanNaming = context.get(CONTEXT_KEY);
    return servletSpanNaming == null ? NoopServletSpanNaming.INSTANCE : servletSpanNaming;
  }

  private ServletSpanNaming() {}

  /**
   * This should be called before servlet instrumentation updates the server span name. If it
   * returns true, the servlet instrumentation should update the server span name and then should
   * call {@link #setServletUpdatedServerSpanName}.
   */
  public abstract boolean shouldServletUpdateServerSpanName();

  /** This should be called after servlet instrumentation updates the server span name. */
  public abstract void setServletUpdatedServerSpanName();

  /**
   * This should be called before controller instrumentation updates the server span name. If it
   * returns true, the controller instrumentation should update the server span name and then should
   * call {@link #setControllerUpdatedServerSpanName}.
   */
  public abstract boolean shouldControllerUpdateServerSpanName();

  /** This should be called after controller instrumentation updates the server span name. */
  public abstract void setControllerUpdatedServerSpanName();

  private static class DefaultServletSpanNaming extends ServletSpanNaming {

    private volatile boolean servletUpdatedServerSpanName;
    private volatile boolean controllerUpdatedServerSpanName;

    @Override
    public boolean shouldServletUpdateServerSpanName() {
      return !servletUpdatedServerSpanName;
    }

    @Override
    public void setServletUpdatedServerSpanName() {
      servletUpdatedServerSpanName = true;
    }

    @Override
    public boolean shouldControllerUpdateServerSpanName() {
      return !controllerUpdatedServerSpanName;
    }

    @Override
    public void setControllerUpdatedServerSpanName() {
      controllerUpdatedServerSpanName = true;
      servletUpdatedServerSpanName = true; // just in case not set already
    }
  }

  private static class NoopServletSpanNaming extends ServletSpanNaming {

    private static final ServletSpanNaming INSTANCE = new NoopServletSpanNaming();

    @Override
    public boolean shouldServletUpdateServerSpanName() {
      return true;
    }

    @Override
    public void setServletUpdatedServerSpanName() {}

    @Override
    public boolean shouldControllerUpdateServerSpanName() {
      return true;
    }

    @Override
    public void setControllerUpdatedServerSpanName() {}
  }
}
