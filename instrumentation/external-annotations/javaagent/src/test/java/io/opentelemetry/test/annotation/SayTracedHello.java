/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.api.trace.Span;
import java.util.concurrent.Callable;

public class SayTracedHello {

  @com.appoptics.api.ext.LogMethod
  public String appoptics() {
    Span.current().setAttribute("providerAttr", "AppOptics");
    return "hello!";
  }

  @com.newrelic.api.agent.Trace
  public String newrelic() {
    Span.current().setAttribute("providerAttr", "NewRelic");
    return "hello!";
  }

  @com.signalfx.tracing.api.Trace
  public String signalfx() {
    Span.current().setAttribute("providerAttr", "SignalFx");
    return "hello!";
  }

  @com.tracelytics.api.ext.LogMethod
  public String tracelytics() {
    Span.current().setAttribute("providerAttr", "Tracelytics");
    return "hello!";
  }

  @datadog.trace.api.Trace
  public String datadog() {
    Span.current().setAttribute("providerAttr", "Datadog");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  public String dropwizard() {
    Span.current().setAttribute("providerAttr", "Dropwizard");
    return "hello!";
  }

  @kamon.annotation.Trace("spanName")
  public String kamonold() {
    Span.current().setAttribute("providerAttr", "KamonOld");
    return "hello!";
  }

  @kamon.annotation.api.Trace
  public String kamonnew() {
    Span.current().setAttribute("providerAttr", "KamonNew");
    return "hello!";
  }

  @org.springframework.cloud.sleuth.annotation.NewSpan
  public String sleuth() {
    Span.current().setAttribute("providerAttr", "Sleuth");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  public static String sayHello() {
    Span.current().setAttribute("myattr", "test");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  public static String sayHelloSayHa() {
    Span.current().setAttribute("myattr", "test2");
    return sayHello() + sayHello();
  }

  @io.opentracing.contrib.dropwizard.Trace
  public static String sayError() {
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
