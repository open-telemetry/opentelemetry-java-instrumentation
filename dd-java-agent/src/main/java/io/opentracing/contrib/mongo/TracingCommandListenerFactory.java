package io.opentracing.contrib.mongo;

import io.opentracing.Tracer;

public class TracingCommandListenerFactory {

  public static TracingCommandListener create(final Tracer tracer) {
    return new TracingCommandListener(tracer);
  }
}
