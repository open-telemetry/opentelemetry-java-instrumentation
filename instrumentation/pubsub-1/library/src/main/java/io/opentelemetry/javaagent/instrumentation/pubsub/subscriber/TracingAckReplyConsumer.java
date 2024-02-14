package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import io.opentelemetry.context.Context;

final class TracingAckReplyConsumer implements AckReplyConsumer {
  private final AckReplyConsumer wrapped;
  private final Context context;

  TracingAckReplyConsumer(AckReplyConsumer wrapped, Context context) {
    this.wrapped = wrapped;
    this.context = context;
  }

  @Override
  public void ack() {
    AckReplyHelper.ack(context);
    wrapped.ack();
  }

  @Override
  public void nack() {
    AckReplyHelper.nack(context);
    wrapped.nack();
  }
}
