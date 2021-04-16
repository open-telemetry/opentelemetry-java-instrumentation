/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategies;
import io.opentelemetry.instrumentation.guava.GuavaAsyncSpanEndStrategy;

public class InstrumentationHelper {
  static {
    AsyncSpanEndStrategies.getInstance().registerStrategy(GuavaAsyncSpanEndStrategy.INSTANCE);
  }

  public static void initialize() {}
}
