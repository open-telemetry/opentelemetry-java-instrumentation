package io.opentelemetry.javaagent.instrumentation.pubsub.publisher;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubAttributes;
import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.ResourceAttributes;
import io.opentelemetry.semconv.SemanticAttributes;

import javax.annotation.Nullable;
import java.util.List;

public class PublishBatchHelper {
  private PublishBatchHelper() {}
  public static final Instrumenter<Request, Void> INSTRUMENTER = Instrumenter.<Request, Void>builder(
                  GlobalOpenTelemetry.get(), PubsubUtils.INSTRUMENTATION_NAME, spanNameExtractor())
          .addAttributesExtractor(new PubsubPublisherAttributesExtractor())
          .addSpanLinksExtractor(linksExtractor())
          .buildInstrumenter(SpanKindExtractor.alwaysClient());

  static SpanLinksExtractor<Request> linksExtractor() {
    return (spanLinks, parentContext, request) -> request.msgContexts.forEach(ctx -> {
      if (ctx != null) {
        spanLinks.addLink(Span.fromContext(ctx).getSpanContext());
      }
    });
  }

  static SpanNameExtractor<Request> spanNameExtractor() {
    return req -> PubsubUtils.getSpanName(SemanticAttributes.MessagingOperationValues.PUBLISH, req.topicName);
  }

  static class PubsubPublisherAttributesExtractor implements AttributesExtractor<Request, Void> {
    @Override
    public void onStart(AttributesBuilder attributesBuilder, Context context, Request req) {
      attributesBuilder.put(SemanticAttributes.MESSAGING_OPERATION, SemanticAttributes.MessagingOperationValues.PUBLISH);
      attributesBuilder.put(SemanticAttributes.MESSAGING_SYSTEM, PubsubAttributes.MessagingSystemValues.GCP_PUBSUB);
      attributesBuilder.put(SemanticAttributes.MESSAGING_BATCH_MESSAGE_COUNT, req.messageCount);
      attributesBuilder.put(SemanticAttributes.MESSAGING_DESTINATION_NAME, req.topicName);
      attributesBuilder.put(ResourceAttributes.CLOUD_RESOURCE_ID, req.topicFullResourceName);
    }

    @Override
    public void onEnd(AttributesBuilder attributesBuilder, Context context, Request req, @Nullable Void response, @Nullable Throwable throwable) {
    }
  }

  public static final class Request {
    public final String topicName;
    public final String topicFullResourceName;
    public final int messageCount;
    public final List<Context> msgContexts;

    public Request(String topicName, String topicFullResourceName, int messageCount, List<Context> msgContexts) {
      this.topicName = topicName;
      this.topicFullResourceName = topicFullResourceName;
      this.messageCount = messageCount;
      this.msgContexts = msgContexts;
    }
  }
}
