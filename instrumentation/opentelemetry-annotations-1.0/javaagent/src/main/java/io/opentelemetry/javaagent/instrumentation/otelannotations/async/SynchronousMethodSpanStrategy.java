/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

enum SynchronousMethodSpanStrategy implements MethodSpanStrategy {
  INSTANCE;

  @Override
  public boolean supports(Class<?> returnType) {
    return true;
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {
    tracer.end(context);
    return returnValue;
  }
}
