package io.opentelemetry.test.annotation;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Tracer;
import io.opentracing.contrib.dropwizard.Trace;
import java.util.concurrent.Callable;

public class SayTracedHello {

  private static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

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
