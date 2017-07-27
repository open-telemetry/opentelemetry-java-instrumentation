package com.datadoghq.trace.agent;

import com.datadoghq.trace.DDTags;
import com.datadoghq.trace.Trace;
import io.opentracing.tag.StringTag;
import io.opentracing.util.GlobalTracer;

public class SayTracedHello {

  @Trace(operationName = "SAY_HELLO")
  public static String sayHello() {
    new StringTag(DDTags.SERVICE_NAME).set(GlobalTracer.get().activeSpan(), "test");
    return "hello!";
  }

  @Trace(operationName = "SAY_HA")
  public static String sayHA() {
    new StringTag(DDTags.SERVICE_NAME).set(GlobalTracer.get().activeSpan(), "test");
    new StringTag(DDTags.SPAN_TYPE).set(GlobalTracer.get().activeSpan(), "DB");
    return "HA!!";
  }

  @Trace(operationName = "NEW_TRACE")
  public static String sayHELLOsayHA() {
    new StringTag(DDTags.SERVICE_NAME).set(GlobalTracer.get().activeSpan(), "test2");
    return sayHello() + sayHA();
  }

  @Trace(operationName = "ERROR")
  public static String sayERROR() {
    throw new RuntimeException();
  }
}
