/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.hystrix;

import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.trace.Span;

public class HystrixTracer extends BaseTracer {
  public static final HystrixTracer TRACER = new HystrixTracer();

  private final boolean extraTags;

  private HystrixTracer() {
    extraTags = Config.get().getBooleanProperty("otel.hystrix.tags.enabled", false);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.hystrix";
  }

  public void onCommand(Span span, HystrixInvokableInfo<?> command, String methodName) {
    if (command != null) {
      String commandName = command.getCommandKey().name();
      String groupName = command.getCommandGroup().name();
      boolean circuitOpen = command.isCircuitBreakerOpen();

      String spanName = groupName + "." + commandName + "." + methodName;

      span.updateName(spanName);
      if (extraTags) {
        span.setAttribute("hystrix.command", commandName);
        span.setAttribute("hystrix.group", groupName);
        span.setAttribute("hystrix.circuit-open", circuitOpen);
      }
    }
  }
}
