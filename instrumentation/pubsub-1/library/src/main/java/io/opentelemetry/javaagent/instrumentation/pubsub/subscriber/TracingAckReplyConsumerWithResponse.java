package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.AckReplyConsumerWithResponse;
import com.google.cloud.pubsub.v1.AckResponse;
import io.opentelemetry.context.Context;

final class TracingAckReplyConsumerWithResponse implements AckReplyConsumerWithResponse {
  private final AckReplyConsumerWithResponse wrapped;
  private final Context context;

  TracingAckReplyConsumerWithResponse(AckReplyConsumerWithResponse wrapped, Context context) {
    this.wrapped = wrapped;
    this.context = context;
  }

  @Override
  public ApiFuture<AckResponse> ack() {
    AckReplyHelper.ack(context);
    return wrapped.ack();
  }

  @Override
  public ApiFuture<AckResponse> nack() {
    AckReplyHelper.nack(context);
    return wrapped.nack();
  }
}
