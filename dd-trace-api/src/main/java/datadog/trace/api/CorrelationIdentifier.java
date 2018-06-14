package datadog.trace.api;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class to access the active trace and span ids.
 *
 * <p>Intended for use with MDC frameworks.
 */
public class CorrelationIdentifier {
  private static final AtomicReference<Provider> provider = new AtomicReference<>(Provider.NO_OP);

  public static void registerIfAbsent(Provider p) {
    if (p != null && p != Provider.NO_OP) {
      provider.compareAndSet(Provider.NO_OP, p);
    }
  }

  public static long getTraceId() {
    return provider.get().getTraceId();
  }

  public static long getSpanId() {
    return provider.get().getSpanId();
  }

  public interface Provider {
    long getTraceId();

    long getSpanId();

    Provider NO_OP =
        new Provider() {
          @Override
          public long getTraceId() {
            return 0;
          }

          @Override
          public long getSpanId() {
            return 0;
          }
        };
  }
}
