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

  public static String getTraceId() {
    return provider.get().getTraceId();
  }

  public static String getSpanId() {
    return provider.get().getSpanId();
  }

  public interface Provider {
    String getTraceId();

    String getSpanId();

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
        };
  }
}
