package io.opentelemetry.javaagent.instrumentation.pubsub.publisher;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ResourceAttributes.CLOUD_RESOURCE_ID;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.core.ApiFuture;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubAttributes;
import io.opentelemetry.javaagent.instrumentation.pubsub.subscriber.ReceiveMessageHelper;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TracingMessagePublisherTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  public void batchWithNoMessages() throws Exception {
    PublishRequest originalRequest = PublishRequest.newBuilder()
            .setTopic("projects/proj/topics/top")
            .build();
    PublishResponse originalResponse = PublishResponse.newBuilder().build();
    AtomicReference<PublishRequest> modifiedRequestRef = new AtomicReference<>();
    publish(originalRequest, modifiedRequest -> {
      modifiedRequestRef.set(modifiedRequest);
      return originalResponse;
    });

    assertThat(testing.spans()).hasSize(1);
    assertThat(testing.spans().get(0)).hasName("parent");

    assertThat(modifiedRequestRef.get()).isSameAs(originalRequest);
  }

  @Test
  public void batchWithSingleMessage() throws Exception {
    PubsubMessage originalMsg = PubsubMessage.newBuilder()
            .setData(ByteString.copyFrom("data", "UTF-8"))
            .setOrderingKey("orderkey")
            .build();
    PublishRequest originalRequest = PublishRequest.newBuilder()
            .setTopic("projects/proj/topics/top")
            .addMessages(originalMsg)
            .build();
    PublishResponse originalResponse = PublishResponse.newBuilder().addMessageIds("msg1").build();

    AtomicReference<PublishRequest> modifiedRequestRef = new AtomicReference<>();
    publish(originalRequest, modifiedRequest -> {
      modifiedRequestRef.set(modifiedRequest);
      return originalResponse;
    });

    List<SpanData> spans = testing.spans();

    SpanData parent = spans.stream().filter(s -> s.getName().equals("parent")).findFirst().get();
    SpanData publish = spans.stream().filter(s -> s.getName().equals("top publish")).findFirst().get();
    SpanData create = spans.stream().filter(s -> s.getName().equals("top create")).findFirst().get();

    assertThat(publish)
            .hasAttribute(MESSAGING_OPERATION, "publish")
            .hasAttribute(MESSAGING_SYSTEM, "gcp_pubsub")
            .hasAttribute(MESSAGING_BATCH_MESSAGE_COUNT, 1L)
            .hasAttribute(MESSAGING_DESTINATION_NAME, "top")
            .hasAttribute(CLOUD_RESOURCE_ID, "//pubsub.googleapis.com/projects/proj/topics/top")
            .hasParent(parent)
            .hasKind(SpanKind.CLIENT)
            .hasLinks(LinkData.create(create.getSpanContext()))
            .hasEnded();

    assertThat(create)
            .hasAttribute(MESSAGING_OPERATION, "create")
            .hasAttribute(MESSAGING_SYSTEM, "gcp_pubsub")
            .hasAttribute(MESSAGING_DESTINATION_NAME, "top")
            .hasAttribute(CLOUD_RESOURCE_ID, "//pubsub.googleapis.com/projects/proj/topics/top")
            .hasAttribute(MESSAGING_MESSAGE_BODY_SIZE, (long) originalMsg.getData().size())
            .hasAttribute(MESSAGING_MESSAGE_ID, "msg1")
            .hasAttribute(PubsubAttributes.ORDERING_KEY, "orderkey")
            .hasParent(parent)
            .hasKind(SpanKind.PRODUCER)
            .hasEnded();

    PubsubMessage sentMsg = modifiedRequestRef.get().getMessages(0);
    assertThat(sentMsg.getData().toString("UTF-8")).isEqualTo("data");
    assertThat(getPropagatedSpanId(sentMsg)).isEqualTo(create.getSpanId());
  }

  @Test
  public void batchWithPubsubError() throws Exception {
    PubsubMessage originalMsg = PubsubMessage.newBuilder()
            .setData(ByteString.copyFrom("data", "UTF-8"))
            .build();
    PublishRequest originalRequest = PublishRequest.newBuilder()
            .setTopic("projects/proj/topics/top")
            .addMessages(originalMsg)
            .build();

    RuntimeException error = new RuntimeException("error!");
    AtomicReference<PublishRequest> modifiedRequestRef = new AtomicReference<>();
    assertThatThrownBy(() -> publish(originalRequest, r -> {
      modifiedRequestRef.set(r);
      throw error;
    })).isEqualTo(error);

    List<SpanData> spans = testing.spans();

    SpanData parent = spans.stream().filter(s -> s.getName().equals("parent")).findFirst().get();
    SpanData publish = spans.stream().filter(s -> s.getName().equals("top publish")).findFirst().get();
    SpanData create = spans.stream().filter(s -> s.getName().equals("top create")).findFirst().get();

    assertThat(publish)
            .hasAttribute(MESSAGING_OPERATION, "publish")
            .hasAttribute(MESSAGING_SYSTEM, "gcp_pubsub")
            .hasAttribute(MESSAGING_BATCH_MESSAGE_COUNT, 1L)
            .hasAttribute(MESSAGING_DESTINATION_NAME, "top")
            .hasAttribute(CLOUD_RESOURCE_ID, "//pubsub.googleapis.com/projects/proj/topics/top")
            .hasParent(parent)
            .hasKind(SpanKind.CLIENT)
            .hasLinks(LinkData.create(create.getSpanContext()))
            .hasException(error)
            .hasEnded();

    assertThat(create)
            .hasAttribute(MESSAGING_OPERATION, "create")
            .hasAttribute(MESSAGING_SYSTEM, "gcp_pubsub")
            .hasAttribute(MESSAGING_DESTINATION_NAME, "top")
            .hasAttribute(CLOUD_RESOURCE_ID, "//pubsub.googleapis.com/projects/proj/topics/top")
            .hasAttribute(MESSAGING_MESSAGE_BODY_SIZE, (long) originalMsg.getData().size())
            .hasParent(parent)
            .hasKind(SpanKind.PRODUCER)
            .hasException(error)
            .hasEnded();

    PubsubMessage sentMsg = modifiedRequestRef.get().getMessages(0);
    assertThat(sentMsg.getData().toString("UTF-8")).isEqualTo("data");
    assertThat(getPropagatedSpanId(sentMsg)).isEqualTo(create.getSpanId());
  }

  private static String getPropagatedSpanId(PubsubMessage msg) {
    Context extracted = W3CTraceContextPropagator.getInstance().extract(Context.root(), msg, ReceiveMessageHelper.AttributesGetter.INSTANCE);
    return Span.fromContext(extracted).getSpanContext().getSpanId();
  }

  private static void publish(PublishRequest request, Function<PublishRequest, PublishResponse> responder) {
    List<PublishRequest> modifiedRequests = new ArrayList<>();
    AtomicReference<PublishResponse> responseRef = new AtomicReference<>();
    TracingMessagePublisher tracingMessagePublisher = new TracingMessagePublisher(callable(req -> {
      modifiedRequests.add(req);
      PublishResponse response = responder.apply(req);
      responseRef.set(response);
      return response;
    }));
    PublishResponse returnedResponse = testing.runWithSpan("parent", () -> tracingMessagePublisher.call(request));
    assertThat(returnedResponse).isEqualTo(responseRef.get());
    assertThat(modifiedRequests).hasSize(1);
  }

  private static UnaryCallable<PublishRequest, PublishResponse> callable(Function<PublishRequest, PublishResponse> func) {
    return new UnaryCallable<PublishRequest, PublishResponse>() {
      @Override
      public ApiFuture<PublishResponse> futureCall(PublishRequest publishRequest, ApiCallContext apiCallContext) {
        SettableApiFuture<PublishResponse> future = SettableApiFuture.create();
        try {
          future.set(func.apply(publishRequest));
        } catch (Throwable error) {
          future.setException(error);
        }
        return future;
      }
    };
  }
}
