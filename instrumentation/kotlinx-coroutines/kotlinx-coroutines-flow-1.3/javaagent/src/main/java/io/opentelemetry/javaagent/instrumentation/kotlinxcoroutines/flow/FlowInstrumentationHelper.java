/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.flow;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncEndHandler;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncEndStrategies;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncEndStrategy;
import kotlinx.coroutines.flow.Flow;

public final class FlowInstrumentationHelper {
  private static final FlowAsyncEndStrategy asyncEndStrategy = new FlowAsyncEndStrategy();

  static {
    AsyncEndStrategies.instance().registerStrategy(asyncEndStrategy);
  }

  public static void initialize() {}

  private FlowInstrumentationHelper() {}

  private static final class FlowAsyncEndStrategy implements AsyncEndStrategy {

    @Override
    public boolean supports(Class<?> returnType) {
      return Flow.class.isAssignableFrom(returnType);
    }

    @Override
    public <REQUEST, RESPONSE> Object end(
        AsyncEndHandler<REQUEST, RESPONSE> handler,
        Context context,
        REQUEST request,
        Object asyncValue,
        Class<RESPONSE> responseType) {
      Flow<?> flow = (Flow<?>) asyncValue;
      return FlowUtilKt.onComplete(flow, handler, context, request);
    }
  }
}
