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
package io.opentelemetry.test.annotation;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.Tracer;
import java.util.concurrent.Callable;

public class SayTracedHello {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto");

  @com.appoptics.api.ext.LogMethod
  public String appoptics() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "AppOptics");
    return "hello!";
  }

  @com.newrelic.api.agent.Trace
  public String newrelic() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "NewRelic");
    return "hello!";
  }

  @com.signalfx.tracing.api.Trace
  public String signalfx() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "SignalFx");
    return "hello!";
  }

  @com.tracelytics.api.ext.LogMethod
  public String tracelytics() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Tracelytics");
    return "hello!";
  }

  @datadog.trace.api.Trace
  public String datadog() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Datadog");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  public String dropwizard() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Dropwizard");
    return "hello!";
  }

  @kamon.annotation.Trace("spanName")
  public String kamonold() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "KamonOld");
    return "hello!";
  }

  @kamon.annotation.api.Trace
  public String kamonnew() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "KamonNew");
    return "hello!";
  }

  @org.springframework.cloud.sleuth.annotation.NewSpan
  public String sleuth() {
    TRACER.getCurrentSpan().setAttribute("providerAttr", "Sleuth");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  public static String sayHello() {
    TRACER.getCurrentSpan().setAttribute("myattr", "test");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  public static String sayHELLOsayHA() {
    TRACER.getCurrentSpan().setAttribute("myattr", "test2");
    return sayHello() + sayHello();
  }

  @io.opentracing.contrib.dropwizard.Trace
  public static String sayERROR() {
    throw new RuntimeException();
  }

  public static String fromCallable() {
    return new Callable<String>() {
      @com.newrelic.api.agent.Trace
      @Override
      public String call() {
        return "Howdy!";
      }
    }.call();
  }

  public static String fromCallableWhenDisabled() {
    return new Callable<String>() {
      @com.newrelic.api.agent.Trace
      @Override
      public String call() {
        return "Howdy!";
      }
    }.call();
  }
}
