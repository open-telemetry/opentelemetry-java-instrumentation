/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.external.annotations;

import io.opentelemetry.api.trace.Span;
import java.util.concurrent.Callable;

// To better see which library is tested
@SuppressWarnings("UnnecessarilyFullyQualified")
class SayTracedHello {

  SayTracedHello() {}

  // used to verify that constructor with tracing annotation doesn't break instrumentation
  @com.appoptics.api.ext.LogMethod
  SayTracedHello(String unused) {}

  @com.appoptics.api.ext.LogMethod
  String appoptics() {
    Span.current().setAttribute("providerAttr", "AppOptics");
    return "hello!";
  }

  @com.newrelic.api.agent.Trace
  String newrelic() {
    Span.current().setAttribute("providerAttr", "NewRelic");
    return "hello!";
  }

  @com.signalfx.tracing.api.Trace
  String signalfx() {
    Span.current().setAttribute("providerAttr", "SignalFx");
    return "hello!";
  }

  @com.tracelytics.api.ext.LogMethod
  String tracelytics() {
    Span.current().setAttribute("providerAttr", "Tracelytics");
    return "hello!";
  }

  @datadog.trace.api.Trace
  String datadog() {
    Span.current().setAttribute("providerAttr", "Datadog");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  String dropwizard() {
    Span.current().setAttribute("providerAttr", "Dropwizard");
    return "hello!";
  }

  @kamon.annotation.Trace("spanName")
  String kamonold() {
    Span.current().setAttribute("providerAttr", "KamonOld");
    return "hello!";
  }

  @kamon.annotation.api.Trace
  String kamonnew() {
    Span.current().setAttribute("providerAttr", "KamonNew");
    return "hello!";
  }

  @org.springframework.cloud.sleuth.annotation.NewSpan
  String sleuth() {
    Span.current().setAttribute("providerAttr", "Sleuth");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  static String sayHello() {
    Span.current().setAttribute("myattr", "test");
    return "hello!";
  }

  @io.opentracing.contrib.dropwizard.Trace
  static String sayHelloSayHa() {
    Span.current().setAttribute("myattr", "test2");
    return sayHello() + sayHello();
  }

  @io.opentracing.contrib.dropwizard.Trace
  static String sayError() {
    throw new IllegalStateException();
  }

  static String fromCallable() {
    return new Callable<String>() {
      @com.newrelic.api.agent.Trace
      @Override
      public String call() {
        return "Howdy!";
      }
    }.call();
  }

  static String fromCallableWhenDisabled() {
    return new Callable<String>() {
      @com.newrelic.api.agent.Trace
      @Override
      public String call() {
        return "Howdy!";
      }
    }.call();
  }
}
