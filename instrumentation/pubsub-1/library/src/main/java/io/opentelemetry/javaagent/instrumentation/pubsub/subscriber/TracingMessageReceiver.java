package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.MessageReceiverWithAckResponse;
import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TracingMessageReceiver {
  private static final Logger logger = Logger.getLogger(TracingMessageReceiver.class.getName());
  private final Instrumenter<PubsubMessage, Void> instrumenter;

  public TracingMessageReceiver(Instrumenter<PubsubMessage, Void> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public MessageReceiver build(MessageReceiver wrapped) {
    return (msg, ack) -> {
      if (!receiveMessageWithTracing(
          ctx -> wrapped.receiveMessage(msg, new TracingAckReplyConsumer(ack, ctx)), msg)) {
        wrapped.receiveMessage(msg, ack);
      }
    };
  }

  public MessageReceiverWithAckResponse buildWithAckResponse(
      MessageReceiverWithAckResponse wrapped) {
    return (msg, ack) -> {
      if (!receiveMessageWithTracing(
          ctx -> wrapped.receiveMessage(msg, new TracingAckReplyConsumerWithResponse(ack, ctx)),
          msg)) {
        wrapped.receiveMessage(msg, ack);
      }
    };
  }

  private boolean receiveMessageWithTracing(Consumer<Context> receiver, PubsubMessage msg) {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, msg)) {
      return false;
    }
    Context context;
    try {
      context = instrumenter.start(parentContext, msg);
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Error starting pubsub subscriber span", t);
      return false;
    }
    try (Scope scope = context.makeCurrent()) {
      RuntimeException error = null;
      try {
        receiver.accept(context);
      } catch (RuntimeException e) {
        error = e;
      }
      try {
        instrumenter.end(context, msg, null, error);
      } catch (Throwable t) {
        logger.log(Level.WARNING, "Error ending pubsub subscriber span", t);
      }
      if (error != null) {
        throw error;
      }
      return true;
    }
  }
}
