/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class HibernateTracer extends BaseTracer {
  private static final HibernateTracer TRACER = new HibernateTracer();

  public static HibernateTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Context parentContext, String operationName, String entityName) {
    return startSpan(parentContext, spanNameForOperation(operationName, entityName));
  }

  public Context startSpan(Context parentContext, String spanName) {
    return startSpan(parentContext, spanName, SpanKind.INTERNAL);
  }

  private static String spanNameForOperation(String operationName, String entityName) {
    if (entityName != null) {
      return operationName + " " + entityName;
    }
    return operationName;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.hibernate-common";
  }
}
