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
public abstract class ServerSpanNaming {

  private static final ContextKey<ServerSpanNaming> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-span-naming-key");

  public static Context init(Context context) {
    if (context.get(CONTEXT_KEY) != null) {
      return context;
    }
    return context.with(CONTEXT_KEY, new DefaultServerSpanNaming());
  }

  public static ServerSpanNaming from(Context context) {
    ServerSpanNaming serverSpanNaming = context.get(CONTEXT_KEY);
    return serverSpanNaming == null ? NoopServerSpanNaming.INSTANCE : serverSpanNaming;
  }

  private ServerSpanNaming() {}

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

  private static class DefaultServerSpanNaming extends ServerSpanNaming {

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

  private static class NoopServerSpanNaming extends ServerSpanNaming {

    private static final ServerSpanNaming INSTANCE = new NoopServerSpanNaming();

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
