package io.opentelemetry.javaagent.instrumentation.pubsub.publisher;

import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubAttributes;
import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubUtils;
import io.opentelemetry.semconv.ResourceAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

public class PublishMessageHelper {
  private PublishMessageHelper() {}
  public static final Instrumenter<Request, PublishResponse> INSTRUMENTER = Instrumenter.<Request, PublishResponse>builder(
                  GlobalOpenTelemetry.get(), PubsubUtils.INSTRUMENTATION_NAME, spanNameExtractor())
          .addAttributesExtractor(new PubsubPublisherAttributesExtractor())
          .buildProducerInstrumenter(AttributesSetter.INSTANCE);

  static SpanNameExtractor<Request> spanNameExtractor() {
    return req -> PubsubUtils.getSpanName("create", req.topicName);
  }

  static class PubsubPublisherAttributesExtractor implements AttributesExtractor<Request, PublishResponse> {

    @Override
    public void onStart(AttributesBuilder attributesBuilder, Context context, Request req) {
      attributesBuilder.put(SemanticAttributes.MESSAGING_OPERATION, "create");
      attributesBuilder.put(SemanticAttributes.MESSAGING_SYSTEM, PubsubAttributes.MessagingSystemValues.GCP_PUBSUB);
      attributesBuilder.put(SemanticAttributes.MESSAGING_DESTINATION_NAME, req.topicName);
      attributesBuilder.put(ResourceAttributes.CLOUD_RESOURCE_ID, req.topicFullResourceName);
      attributesBuilder.put(SemanticAttributes.MESSAGING_MESSAGE_BODY_SIZE, req.msg.getData().size());
      if (!req.msg.getOrderingKey().isEmpty()) {
        attributesBuilder.put(PubsubAttributes.ORDERING_KEY, req.msg.getOrderingKey());
      }
    }

    @Override
    public void onEnd(AttributesBuilder attributesBuilder, Context context, Request req, @Nullable PublishResponse response, @Nullable Throwable throwable) {
      if (response != null) {
        attributesBuilder.put(SemanticAttributes.MESSAGING_MESSAGE_ID, response.getMessageIds(req.index));
      }
    }
  }

  public enum AttributesSetter implements TextMapSetter<Request> {
    INSTANCE;

    @Override
    public void set(@Nullable Request req, String key, String value) {
      if (req != null) {
        req.msg.putAttributes(key, value);
      }
    }
  }

  public static List<Request> deconstructRequest(String topicName, String topicFullResourceName, List<PubsubMessage> messages) {
    return IntStream.range(0, messages.size())
            .mapToObj(i -> new Request(topicName, topicFullResourceName, messages.get(i).toBuilder(), i))
            .collect(Collectors.toList());
  }

  public static PublishRequest reconstructRequest(PublishRequest original, List<Request> reqs) {
    return original.toBuilder()
            .clearMessages()
            .addAllMessages(reqs.stream().map(r -> r.msg.build()).collect(Collectors.toList()))
            .build();
  }

  public static final class Request {
    public final String topicName;
    public final String topicFullResourceName;
    public final PubsubMessage.Builder msg;
    public final int index;

    public Request(String topicName, String topicFullResourceName, PubsubMessage.Builder msg, int index) {
      this.topicName = topicName;
      this.topicFullResourceName = topicFullResourceName;
      this.msg = msg;
      this.index = index;
    }
  }
}
