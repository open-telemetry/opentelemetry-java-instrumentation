/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import javax.annotation.Nullable;

public interface MethodSpanStrategy extends ImplicitContextKeyed {
  Object end(Object result, BaseTracer tracer, Context context);

  @Override
  default Context storeInContext(Context context) {
    return context.with(MethodSpanStrategyContextKey.KEY, this);
  }

  @Nullable
  static MethodSpanStrategy fromContextOrNull(Context context) {
    return context.get(MethodSpanStrategyContextKey.KEY);
  }
}
