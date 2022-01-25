/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines;

import io.opentelemetry.context.Context;
import io.opentelemetry.extension.kotlin.ContextExtensionsKt;
import kotlin.coroutines.CoroutineContext;

public final class KotlinCoroutinesInstrumentationHelper {

  public static CoroutineContext addOpenTelemetryContext(CoroutineContext coroutineContext) {
    Context current = Context.current();
    return addOpenTelemetryContext(coroutineContext, current);
  }

  public static CoroutineContext addOpenTelemetryContext(
      CoroutineContext coroutineContext, Context otelContext) {
    Context inCoroutine = ContextExtensionsKt.getOpenTelemetryContext(coroutineContext);
    if (otelContext == inCoroutine) {
      return coroutineContext;
    }
    return coroutineContext.plus(ContextExtensionsKt.asContextElement(otelContext));
  }

  private KotlinCoroutinesInstrumentationHelper() {}
}
