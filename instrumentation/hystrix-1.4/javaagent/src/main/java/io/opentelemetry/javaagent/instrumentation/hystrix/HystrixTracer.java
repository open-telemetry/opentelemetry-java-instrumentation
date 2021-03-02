/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hystrix;

import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class HystrixTracer extends BaseTracer {
  private static final HystrixTracer TRACER = new HystrixTracer();

  public static HystrixTracer tracer() {
    return TRACER;
  }

  private final boolean captureExperimentalSpanAttributes =
      Config.get()
          .getBooleanProperty("otel.instrumentation.hystrix.experimental-span-attributes", false);

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.hystrix-1.4";
  }

  public void onCommand(Span span, HystrixInvokableInfo<?> command, String methodName) {
    if (command != null) {
      String commandName = command.getCommandKey().name();
      String groupName = command.getCommandGroup().name();
      boolean circuitOpen = command.isCircuitBreakerOpen();

      String spanName = groupName + "." + commandName + "." + methodName;

      span.updateName(spanName);
      if (captureExperimentalSpanAttributes) {
        span.setAttribute("hystrix.command", commandName);
        span.setAttribute("hystrix.group", groupName);
        span.setAttribute("hystrix.circuit-open", circuitOpen);
      }
    }
  }
}
