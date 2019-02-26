package datadog.trace.agent.test.utils;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Field;

public class GlobalTracerUtils {
  public static void registerOrReplaceGlobalTracer(final Tracer tracer) {
    try {
      GlobalTracer.register(tracer);
    } catch (final Exception e) {
      // Force it anyway using reflection
      Field field = null;
      try {
        field = GlobalTracer.class.getDeclaredField("tracer");
        field.setAccessible(true);
        field.set(null, tracer);
      } catch (final Exception e2) {
        throw new IllegalStateException(e2);
      } finally {
        if (null != field) {
          field.setAccessible(false);
        }
      }
    }

    if (!GlobalTracer.isRegistered()) {
      throw new RuntimeException("Unable to register the global tracer.");
    }
  }

  /** Get the tracer implementation out of the GlobalTracer */
  public static Tracer getUnderlyingGlobalTracer() {
    Field field = null;
    try {
      field = GlobalTracer.class.getDeclaredField("tracer");
      field.setAccessible(true);
      return (Tracer) field.get(GlobalTracer.get());
    } catch (final Exception e2) {
      throw new IllegalStateException(e2);
    } finally {
      if (null != field) {
        field.setAccessible(false);
      }
    }
  }
}
