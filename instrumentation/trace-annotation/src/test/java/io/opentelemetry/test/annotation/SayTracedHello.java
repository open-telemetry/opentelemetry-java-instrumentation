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
package io.opentelemetry.test.annotation;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Tracer;
import io.opentracing.contrib.dropwizard.Trace;
import java.util.concurrent.Callable;

public class SayTracedHello {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto");

  @Trace
  public static String sayHello() {
    TRACER.getCurrentSpan().setAttribute(MoreTags.SERVICE_NAME, "test");
    return "hello!";
  }

  @Trace
  public static String sayHELLOsayHA() {
    TRACER.getCurrentSpan().setAttribute(MoreTags.SERVICE_NAME, "test2");
    return sayHello() + sayHello();
  }

  @Trace
  public static String sayERROR() {
    throw new RuntimeException();
  }

  public static String fromCallable() throws Exception {
    return new Callable<String>() {
      @com.newrelic.api.agent.Trace
      @Override
      public String call() throws Exception {
        return "Howdy!";
      }
    }.call();
  }

  public static String fromCallableWhenDisabled() throws Exception {
    return new Callable<String>() {
      @com.newrelic.api.agent.Trace
      @Override
      public String call() throws Exception {
        return "Howdy!";
      }
    }.call();
  }
}
