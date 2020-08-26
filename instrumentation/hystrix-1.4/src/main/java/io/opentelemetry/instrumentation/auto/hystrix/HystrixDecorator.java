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
import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.cache.Caches;
import io.opentelemetry.instrumentation.api.cache.Functions;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.decorator.BaseDecorator;
import io.opentelemetry.trace.Span;
import java.util.Objects;

public class HystrixDecorator extends BaseDecorator {
  public static final HystrixDecorator DECORATE = new HystrixDecorator();

  private final boolean extraTags;

  private HystrixDecorator() {
    extraTags = Config.get().isHystrixTagsEnabled();
  }

  private static final Cache<ResourceNameCacheKey, String> RESOURCE_NAME_CACHE =
      Caches.newFixedSizeCache(64);

  private static final Functions.ToString<ResourceNameCacheKey> TO_STRING =
      new Functions.ToString<>();

  private static final class ResourceNameCacheKey {
    private final String group;
    private final String command;
    private final String methodName;

    private ResourceNameCacheKey(String group, String command, String methodName) {
      this.group = group;
      this.command = command;
      this.methodName = methodName;
    }

    @Override
    public String toString() {
      return group + "." + command + "." + methodName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ResourceNameCacheKey cacheKey = (ResourceNameCacheKey) o;
      return group.equals(cacheKey.group)
          && command.equals(cacheKey.command)
          && methodName.equals(cacheKey.methodName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(group, command, methodName);
    }
  }

  public void onCommand(Span span, HystrixInvokableInfo<?> command, String methodName) {
    if (command != null) {
      String commandName = command.getCommandKey().name();
      String groupName = command.getCommandGroup().name();
      boolean circuitOpen = command.isCircuitBreakerOpen();

      String spanName =
          RESOURCE_NAME_CACHE.computeIfAbsent(
              new ResourceNameCacheKey(groupName, commandName, methodName), TO_STRING);

      span.updateName(spanName);
      if (extraTags) {
        span.setAttribute("hystrix.command", commandName);
        span.setAttribute("hystrix.group", groupName);
        span.setAttribute("hystrix.circuit-open", circuitOpen);
      }
    }
  }
}
