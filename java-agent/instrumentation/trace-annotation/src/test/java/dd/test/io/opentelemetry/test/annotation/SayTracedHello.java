package io.opentelemetry.test.annotation;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.Trace;
import java.util.concurrent.Callable;

public class SayTracedHello {

  @Trace
  public static String sayHello() {
    activeSpan().setAttribute(MoreTags.SERVICE_NAME, "test");
    return "hello!";
  }

  @Trace(resourceName = "WORLD")
  public static String sayHelloOnlyResourceSet() {
    activeSpan().setAttribute(MoreTags.SERVICE_NAME, "test");
    return "hello!";
  }

  @Trace(operationName = "SAY_HA")
  public static String sayHA() {
    activeSpan().setAttribute(MoreTags.SERVICE_NAME, "test");
    activeSpan().setAttribute(MoreTags.SPAN_TYPE, "DB");
    return "HA!!";
  }

  @Trace(operationName = "SAY_HA", resourceName = "EARTH")
  public static String sayHAWithResource() {
    activeSpan().setAttribute(MoreTags.SERVICE_NAME, "test");
    activeSpan().setAttribute(MoreTags.SPAN_TYPE, "DB");
    return "HA EARTH!!";
  }

  @Trace(operationName = "NEW_TRACE")
  public static String sayHELLOsayHA() {
    activeSpan().setAttribute(MoreTags.SERVICE_NAME, "test2");
    return sayHello() + sayHA();
  }

  @Trace(operationName = "NEW_TRACE", resourceName = "WORLD")
  public static String sayHELLOsayHAWithResource() {
    activeSpan().setAttribute(MoreTags.SERVICE_NAME, "test2");
    return sayHello() + sayHA();
  }

  @Trace(operationName = "NEW_TRACE", resourceName = "WORLD")
  public static String sayHELLOsayHAMixedResourceChildren() {
    activeSpan().setAttribute(MoreTags.SERVICE_NAME, "test2");
    return sayHello() + sayHAWithResource();
  }

  @Trace(operationName = "ERROR")
  public static String sayERROR() {
    throw new RuntimeException();
  }

  @Trace(operationName = "ERROR", resourceName = "WORLD")
  public static String sayERRORWithResource() {
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
