/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.hystrix;

import com.netflix.hystrix.HystrixInvokableInfo;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;

public class HystrixDecorator extends BaseDecorator {
  public static final HystrixDecorator DECORATE = new HystrixDecorator();

  @Override
  protected String getComponentName() {
    return "hystrix";
  }

  public void onCommand(
      final Span span, final HystrixInvokableInfo<?> command, final String methodName) {
    if (command != null) {
      final String commandName = command.getCommandKey().name();
      final String groupName = command.getCommandGroup().name();
      final boolean circuitOpen = command.isCircuitBreakerOpen();

      final String resourceName = groupName + "." + commandName + "." + methodName;

      span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);
      span.setAttribute("hystrix.command", commandName);
      span.setAttribute("hystrix.group", groupName);
      span.setAttribute("hystrix.circuit-open", circuitOpen);
    }
  }
}
