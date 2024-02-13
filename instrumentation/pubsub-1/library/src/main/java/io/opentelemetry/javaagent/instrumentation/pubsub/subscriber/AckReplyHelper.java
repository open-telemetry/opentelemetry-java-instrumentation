package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

public class AckReplyHelper {
  private AckReplyHelper() {}

  public static void ack(Context context) {
    Span.fromContext(context).setAttribute(PubsubAttributes.ACK_RESULT, PubsubAttributes.AckResultValues.ACK);
  }

  public static void nack(Context context) {
    Span.fromContext(context).setAttribute(PubsubAttributes.ACK_RESULT, PubsubAttributes.AckResultValues.NACK);
  }
}
