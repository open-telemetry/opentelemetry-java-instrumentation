package io.opentelemetry.javaagent.bootstrap.context;


import javax.annotation.Nullable;
import java.util.Random;

public class TraceContext {
  private static final InheritableThreadLocal<TraceContextHolder> contextHolder = new InheritableThreadLocal<TraceContextHolder>();

  private static final Random random = new Random();

  @Nullable
  public static TraceContextHolder currentTraceContext() {
    return contextHolder.get();
  }

  @Nullable
  public String getCurrentTraceId() {
    return contextHolder.get().getTraceId();
  }

  public void setCurrentTraceId(String traceId) {
    contextHolder.get().setTraceId(traceId);
  }

  public static void setCurrentContext(
      @Nullable String traceIdKey, @Nullable String spanIdKey, @Nullable String traceId,
      @Nullable String spanId, @Nullable String parentSpanId) {
    contextHolder.set(new TraceContextHolder(traceIdKey, spanIdKey, traceId, spanId, parentSpanId));
  }


  @Nullable
  public static String getNewSpanId() {
    return Long.toHexString(random.nextLong());
  }

  public void clear() {
    contextHolder.remove();
  }

  public static void extractTraceContext(String traceId, String spanId, String traceIdKey,
      String spanIdKey) {
    String spanIdGenerated = getNewSpanId();
    if (traceId == null || traceId.isEmpty()) {
      traceId = spanIdGenerated;
    }
    setCurrentContext(traceIdKey, spanIdKey, traceId, spanIdGenerated, spanId);
  }
}
