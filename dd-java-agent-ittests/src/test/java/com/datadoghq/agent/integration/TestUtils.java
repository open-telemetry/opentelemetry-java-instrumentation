package com.datadoghq.agent.integration;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;

public class TestUtils {
  public static void registerOrReplaceGlobalTracer(Tracer tracer) throws Exception {
    try {
      GlobalTracer.register(tracer);
    } catch (final Exception e) {
      // Force it anyway using reflection
      final Field field = GlobalTracer.class.getDeclaredField("tracer");
      field.setAccessible(true);
      field.set(null, tracer);
    }
  }
}
