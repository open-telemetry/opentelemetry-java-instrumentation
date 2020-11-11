package io.opentelemetry.demo;

import io.opentelemetry.sdk.trace.IdGenerator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom {@link IdGenerator} which provides span and trace ids.
 *
 * @see io.opentelemetry.sdk.trace.TracerSdkProvider
 * @see DemoTracerCustomizer
 */
public class DemoIdGenerator implements IdGenerator {
  private static final AtomicLong traceId = new AtomicLong(0);
  private static final AtomicLong spanId = new AtomicLong(0);

  @Override
  public String generateSpanId() {
    return String.valueOf(spanId.incrementAndGet());
  }

  @Override
  public String generateTraceId() {
    return String.valueOf(traceId.incrementAndGet());
  }
}
