/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
