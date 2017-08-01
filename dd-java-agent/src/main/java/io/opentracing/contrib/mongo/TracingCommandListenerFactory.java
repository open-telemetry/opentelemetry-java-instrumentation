package io.opentracing.contrib.mongo;

import io.opentracing.Tracer;

/**
 * This class exists purely to bypass the reduction in constructor visibility of
 * TracingCommandListener.
 */
public class TracingCommandListenerFactory {

  public static TracingCommandListener create(final Tracer tracer) {
    return new TracingCommandListener(tracer);
  }
}
