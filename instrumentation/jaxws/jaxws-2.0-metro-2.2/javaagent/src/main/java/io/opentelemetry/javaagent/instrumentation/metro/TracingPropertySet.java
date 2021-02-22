/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import com.sun.xml.ws.api.PropertySet;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class TracingPropertySet extends PropertySet {
  public static final String CONTEXT_KEY = "TracingPropertySet.Context";
  public static final String SCOPE_KEY = "TracingPropertySet.Scope";
  public static final String THROWABLE_KEY = "TracingPropertySet.Throwable";

  private static final PropertyMap model;

  static {
    model = parse(TracingPropertySet.class);
  }

  private final Context context;
  private final Scope scope;
  private final ThrowableHolder throwableHolder;

  TracingPropertySet(Context context, Scope scope) {
    this.context = context;
    this.scope = scope;
    this.throwableHolder = new ThrowableHolder();
  }

  @Property(CONTEXT_KEY)
  public Context getContext() {
    return context;
  }

  @Property(SCOPE_KEY)
  public Scope getScope() {
    return scope;
  }

  @Property(THROWABLE_KEY)
  public ThrowableHolder getThrowableHolder() {
    return throwableHolder;
  }

  @Override
  protected PropertyMap getPropertyMap() {
    return model;
  }

  public static class ThrowableHolder {
    private Throwable throwable;

    public void setThrowable(Throwable throwable) {
      this.throwable = throwable;
    }

    public Throwable getThrowable() {
      return throwable;
    }
  }
}
