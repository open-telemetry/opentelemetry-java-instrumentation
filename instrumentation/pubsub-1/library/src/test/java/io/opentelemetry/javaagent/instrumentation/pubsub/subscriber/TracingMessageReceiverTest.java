package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubAttributes;
import io.opentelemetry.javaagent.instrumentation.pubsub.publisher.PublishMessageHelper;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_RESOURCE_ID;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_CLIENT_ID;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TracingMessageReceiverTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static final String SUBSCRIPTION = "projects/proj/subscriptions/sub";

  private static PubsubMessage injectParentSpan(PubsubMessage originalMsg) {
    List<PublishMessageHelper.Request> reqs = PublishMessageHelper.deconstructRequest(
        "top",
        "//pubsub.googleapis.com/projects/proj/topics/top",
        Lists.newArrayList(originalMsg));
    testing.runWithSpan("parent", () -> reqs.forEach(req -> W3CTraceContextPropagator.getInstance().inject(Context.current(), req, PublishMessageHelper.AttributesSetter.INSTANCE)));
    return PublishMessageHelper.reconstructRequest(PublishRequest.getDefaultInstance(), reqs).getMessages(0);
  }

  private static PubsubMessage message(String msgId, String data, String orderKey) throws Exception {
    return injectParentSpan(
            PubsubMessage.newBuilder()
                    .setMessageId(msgId)
                    .setData(ByteString.copyFrom(data, "UTF-8"))
                    .setOrderingKey(orderKey)
                    .build());
  }

  @Test
  public void receiveAndAck() throws Exception {
    PubsubMessage originalMsg = message("msg1", "data", "orderKey");
    MessageReceiver instrumented = instrument((req, ack) -> ack.ack());
    instrumented.receiveMessage(originalMsg, replyConsumer());

    List<SpanData> spans = testing.spans();

    SpanData parent = spans.stream().filter(s -> s.getName().equals("parent")).findFirst().get();
    SpanData receive = spans.stream().filter(s -> s.getName().equals("sub receive")).findFirst().get();

    assertThat(receive)
            .hasAttribute(MESSAGING_CLIENT_ID, "sub")
            .hasAttribute(CLOUD_RESOURCE_ID, "//pubsub.googleapis.com/projects/proj/subscriptions/sub")
            .hasAttribute(MESSAGING_OPERATION, "receive")
            .hasAttribute(MESSAGING_SYSTEM, "gcp_pubsub")
            .hasAttribute(MESSAGING_MESSAGE_ID, "msg1")
            .hasAttribute(PubsubAttributes.MESSAGE_ENVELOPE_SIZE, (long) originalMsg.getSerializedSize())
            .hasAttribute(PubsubAttributes.MESSAGE_BODY_SIZE, (long) originalMsg.getData().size())
            .hasAttribute(PubsubAttributes.ACK_RESULT, PubsubAttributes.AckResultValues.ACK)
            .hasAttribute(PubsubAttributes.ORDERING_KEY, "orderKey")
            .hasParent(parent)
            .hasKind(SpanKind.CONSUMER)
            .hasEnded();
  }

  @Test
  public void receiveAndNack() throws Exception {
    PubsubMessage originalMsg = message("msg1", "data", "orderKey");
    MessageReceiver instrumented = instrument((req, ack) -> ack.nack());
    instrumented.receiveMessage(originalMsg, replyConsumer());

    List<SpanData> spans = testing.spans();

    SpanData parent = spans.stream().filter(s -> s.getName().equals("parent")).findFirst().get();
    SpanData receive = spans.stream().filter(s -> s.getName().equals("sub receive")).findFirst().get();

    assertThat(receive)
            .hasAttribute(MESSAGING_CLIENT_ID, "sub")
            .hasAttribute(CLOUD_RESOURCE_ID, "//pubsub.googleapis.com/projects/proj/subscriptions/sub")
            .hasAttribute(MESSAGING_OPERATION, "receive")
            .hasAttribute(MESSAGING_SYSTEM, "gcp_pubsub")
            .hasAttribute(MESSAGING_MESSAGE_ID, "msg1")
            .hasAttribute(PubsubAttributes.MESSAGE_ENVELOPE_SIZE, (long) originalMsg.getSerializedSize())
            .hasAttribute(PubsubAttributes.MESSAGE_BODY_SIZE, (long) originalMsg.getData().size())
            .hasAttribute(PubsubAttributes.ACK_RESULT, PubsubAttributes.AckResultValues.NACK)
            .hasAttribute(PubsubAttributes.ORDERING_KEY, "orderKey")
            .hasParent(parent)
            .hasKind(SpanKind.CONSUMER)
            .hasEnded();
  }

  @Test
  public void receiveAndThrow() throws Exception {
    PubsubMessage originalMsg = message("msg1", "data", "orderKey");
    RuntimeException error = new RuntimeException("oh no");
    MessageReceiver instrumented = instrument((req, ack) -> {
      throw error;
    });

    assertThatThrownBy(() -> instrumented.receiveMessage(originalMsg, replyConsumer()))
            .isEqualTo(error);

    List<SpanData> spans = testing.spans();

    SpanData parent = spans.stream().filter(s -> s.getName().equals("parent")).findFirst().get();
    SpanData receive = spans.stream().filter(s -> s.getName().equals("sub receive")).findFirst().get();

    assertThat(receive)
            .hasAttribute(MESSAGING_CLIENT_ID, "sub")
            .hasAttribute(CLOUD_RESOURCE_ID, "//pubsub.googleapis.com/projects/proj/subscriptions/sub")
            .hasAttribute(MESSAGING_OPERATION, "receive")
            .hasAttribute(MESSAGING_SYSTEM, "gcp_pubsub")
            .hasAttribute(MESSAGING_MESSAGE_ID, "msg1")
            .hasAttribute(PubsubAttributes.MESSAGE_ENVELOPE_SIZE, (long) originalMsg.getSerializedSize())
            .hasAttribute(PubsubAttributes.MESSAGE_BODY_SIZE, (long) originalMsg.getData().size())
            .hasAttribute(PubsubAttributes.ORDERING_KEY, "orderKey")
            .hasException(error)
            .hasParent(parent)
            .hasKind(SpanKind.CONSUMER)
            .hasEnded();
  }

  private static AckReplyConsumer replyConsumer() {
    return new AckReplyConsumer() {
      @Override
      public void ack() {
        AckReplyHelper.ack(Context.current());
      }

      @Override
      public void nack() {
        AckReplyHelper.nack(Context.current());
      }
    };
  }

  private static MessageReceiver instrument(MessageReceiver receiver) {
    return new TracingMessageReceiver(ReceiveMessageHelper.of(SUBSCRIPTION).instrumenter()).build(receiver);
  }

}
