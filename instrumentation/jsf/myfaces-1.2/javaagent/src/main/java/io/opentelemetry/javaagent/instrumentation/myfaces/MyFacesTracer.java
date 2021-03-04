/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.myfaces;

import io.opentelemetry.instrumentation.jsf.JsfTracer;
import javax.el.ELException;

public class MyFacesTracer extends JsfTracer {
  private static final MyFacesTracer TRACER = new MyFacesTracer();

  public static MyFacesTracer tracer() {
    return TRACER;
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    throwable = super.unwrapThrowable(throwable);

    while (throwable instanceof ELException) {
      throwable = throwable.getCause();
    }

    return throwable;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.myfaces-1.2";
  }
}
