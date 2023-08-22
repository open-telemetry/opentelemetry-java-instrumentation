/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.messagehandler.MessageHandler;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.sqs.model.Message;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

public abstract class SqsMessageHandler extends MessageHandler<Message> {
  private final OpenTelemetry openTelemetry;
  private final String destination;
  private SpanKindExtractor<Collection<Message>> spanKindExtractor;
  private SpanNameExtractor<Collection<Message>> spanNameExtractor;

  public SqsMessageHandler(OpenTelemetry openTelemetry, String destination) {
    this.openTelemetry = openTelemetry;
    this.destination = destination;
    this.spanKindExtractor = SpanKindExtractor.alwaysConsumer();
    spanNameExtractor = e -> destination + " process";
  }

  public void setSpanNameExtactor(SpanNameExtractor<Collection<Message>> spanNameExtractor) {
    this.spanNameExtractor = spanNameExtractor;
  }

  @Override
  protected Instrumenter<Collection<Message>, Void> getMessageInstrumenter() {
    return Instrumenter.<Collection<Message>, Void>builder(
            openTelemetry, "io.opentelemetry.aws-sdk-2.2", spanNameExtractor)
        .addAttributesExtractor(getMessageOperationAttributeExtractor())
        .addSpanLinksExtractor(getSpanLinksExtractor())
        .buildInstrumenter(spanKindExtractor);
  }

  public void setSpanKindExtractor(SpanKindExtractor<Collection<Message>> spanKindExtractor) {
    this.spanKindExtractor = spanKindExtractor;
  }

  protected MessagingAttributesGetter<Collection<Message>, Void>
      getMessageingAttributesGetter() {
    String destination = this.destination;

    return new MessagingAttributesGetter<Collection<Message>, Void>() {
      @Nullable
      @Override
      public String getSystem(Collection<Message> v) {
        return "AmazonSQS";
      }

      @Nullable
      @Override
      @SuppressWarnings({"deprecation"}) // Inheriting from interface
      public String getDestinationKind(Collection<Message> v) {
        return null;
      }

      @Nullable
      @Override
      public String getDestination(Collection<Message> v) {
        return destination;
      }

      @Override
      public boolean isTemporaryDestination(Collection<Message> v) {
        return false;
      }

      @Nullable
      @Override
      public String getConversationId(Collection<Message> v) {
        return null;
      }

      @Nullable
      @Override
      public Long getMessagePayloadSize(Collection<Message> v) {
        long total = 0;

        for (Message message : v) {
          total += getPayloadSize(message);
        }

        return total;
      }

      @Nullable
      @Override
      public Long getMessagePayloadCompressedSize(Collection<Message> v) {
        return null;
      }

      @Nullable
      @Override
      public String getMessageId(Collection<Message> request, Void v) {
        return null;
      }
    };
  }

  protected AttributesExtractor<Collection<Message>, Void> getMessageOperationAttributeExtractor() {
    return MessagingAttributesExtractor.create(
        getMessageingAttributesGetter(), MessageOperation.PROCESS);
  }

  protected SpanLinksExtractor<Collection<Message>> getSpanLinksExtractor() {
    return (spanLinks, parentContext, request) -> {
      for (Message message : request) {
        SpanContext messageSpanCtx = getUpstreamContext(openTelemetry, message);

        if (messageSpanCtx!= null && messageSpanCtx.isValid()) {
          spanLinks.addLink(messageSpanCtx);
        }
      }
    };
  }

  public int getPayloadSize(Message message) {
    return message.body().length();
  }

  public SpanContext getUpstreamContext(OpenTelemetry openTelemetry, Message message) {
    TextMapPropagator messagingPropagator = openTelemetry.getPropagators()
        .getTextMapPropagator();

    Context context = SqsParentContext.ofSystemAttributes(SqsMessageAccess.getAttributes(message));

    if (context == Context.root()) {
      Map<String, SdkPojo> messageAtributes = SqsMessageAccess.getMessageAttributes(message);

      context =
          SqsParentContext.ofMessageAttributes(messageAtributes, messagingPropagator);
    }

    return Span.fromContext(context).getSpanContext();
  }
}
