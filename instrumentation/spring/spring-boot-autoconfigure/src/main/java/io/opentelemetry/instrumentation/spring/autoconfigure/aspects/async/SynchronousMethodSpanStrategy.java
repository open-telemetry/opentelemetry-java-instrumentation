/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public enum SynchronousMethodSpanStrategy implements MethodSpanStrategy {
  INSTANCE;

  @Override
  public Object end(Object result, BaseTracer tracer, Context context) {
    tracer.end(context);
    return result;
  }
}
