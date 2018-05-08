package dd.test.trace.annotation;

import datadog.trace.api.DDTags;
import datadog.trace.api.Trace;
import io.opentracing.tag.StringTag;
import io.opentracing.util.GlobalTracer;
import java.util.concurrent.Callable;

public class SayTracedHello {

  @Trace
  public static String sayHello() {
    new StringTag(DDTags.SERVICE_NAME)
        .set(GlobalTracer.get().scopeManager().active().span(), "test");
    return "hello!";
  }

  @Trace(operationName = "SAY_HA")
  public static String sayHA() {
    new StringTag(DDTags.SERVICE_NAME)
        .set(GlobalTracer.get().scopeManager().active().span(), "test");
    new StringTag(DDTags.SPAN_TYPE).set(GlobalTracer.get().scopeManager().active().span(), "DB");
    return "HA!!";
  }

  @Trace(operationName = "NEW_TRACE")
  public static String sayHELLOsayHA() {
    new StringTag(DDTags.SERVICE_NAME)
        .set(GlobalTracer.get().scopeManager().active().span(), "test2");
    return sayHello() + sayHA();
  }

  @Trace(operationName = "ERROR")
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
