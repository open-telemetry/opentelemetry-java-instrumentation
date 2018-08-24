package datadog.trace.context;

import datadog.trace.api.interceptor.TraceInterceptor;
import java.util.concurrent.atomic.AtomicReference;

/** A global reference to the registered Datadog tracer. */
public class TracerBridge {
  private static final AtomicReference<Provider> provider = new AtomicReference<>(Provider.NO_OP);

  public static void registerIfAbsent(Provider p) {
    if (p != null && p != Provider.NO_OP) {
      provider.compareAndSet(Provider.NO_OP, p);
    }
  }

  public static Provider get() {
    return provider.get();
  }

  /**
   * Add a new interceptor to the tracer. Interceptors with duplicate priority to existing ones are
   * ignored.
   *
   * @param traceInterceptor
   * @return false if an interceptor with same priority exists.
   */
  public static boolean addTraceInterceptor(TraceInterceptor traceInterceptor) {
    return get().addTraceInterceptor(traceInterceptor);
  }

  public interface Provider {
    String getTraceId();

    String getSpanId();

    /**
     * Add a new interceptor to the tracer. Interceptors with duplicate priority to existing ones
     * are ignored.
     *
     * @param traceInterceptor
     * @return false if an interceptor with same priority exists.
     */
    boolean addTraceInterceptor(TraceInterceptor traceInterceptor);

    Provider NO_OP =
        new Provider() {
          @Override
          public String getTraceId() {
            return "0";
          }

          @Override
          public String getSpanId() {
            return "0";
          }

          @Override
          public boolean addTraceInterceptor(TraceInterceptor traceInterceptor) {
            return false;
          }
        };
  }
}
