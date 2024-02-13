package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.MessageReceiverWithAckResponse;
import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TracingMessageReceiver {
  private static final Logger logger = Logger.getLogger(TracingMessageReceiver.class.getName());
  private final Instrumenter<PubsubMessage, Void> instrumenter;

  public TracingMessageReceiver(Instrumenter<PubsubMessage, Void> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public MessageReceiver build(MessageReceiver wrapped) {
    return (msg, ack) -> receiveMessage(() -> wrapped.receiveMessage(msg, ack), msg);
  }

  public MessageReceiverWithAckResponse buildWithAckResponse(MessageReceiverWithAckResponse wrapped) {
    return (msg, ack) -> receiveMessage(() -> wrapped.receiveMessage(msg, ack), msg);
  }

  private void receiveMessage(Runnable receiver, PubsubMessage msg) {
    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, msg)) {
      receiver.run();
      return;
    }
    Context context = instrumenter.start(parentContext, msg);
    try (Scope scope = context.makeCurrent()) {
      Throwable error = null;
      try {
        receiver.run();
      } catch (Throwable t) {
        error = t;
      }
      try {
        instrumenter.end(context, msg, null, error);
      } catch (Throwable t) {
        logger.log(Level.WARNING, "Error ending pubsub subscriber span", t);
      }
      if (error != null) {
        unsafeThrow(error);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void unsafeThrow(Throwable t) throws T {
    throw (T) t;
  }
}
