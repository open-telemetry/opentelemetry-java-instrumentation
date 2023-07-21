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
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.messagehandler.MessageHandler;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.sqs.model.Message;

public abstract class SqsMessageHandler
    extends MessageHandler<Collection<Message>, Void> {
  private final OpenTelemetry openTelemetry;
  private final String destination;
  private final SpanKindExtractor<Collection<Message>> spanKindExtractor;

  public SqsMessageHandler(OpenTelemetry openTelemetry, String destination, SpanKindExtractor<Collection<Message>> spanKindExtractor) {
    this.openTelemetry = openTelemetry;
    this.destination = destination;
    this.spanKindExtractor = spanKindExtractor;
  }

  @Override
  protected Instrumenter<Collection<Message>, Void> getMessageInstrumenter() {
    return Instrumenter.<Collection<Message>, Void>builder(
            openTelemetry, "io.opentelemetry.aws-sdk-2.2", getSpanNameExtractor())
        .addAttributesExtractor(getAttributesExtractor())
        .addSpanLinksExtractor(getSpanLinksExtractor())
        .buildInstrumenter(spanKindExtractor);
  }

  protected SpanNameExtractor<Collection<Message>> getSpanNameExtractor() {
    return MessagingSpanNameExtractor.create(
        getMessageingAttributesGetter(), MessageOperation.PROCESS);
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
          total += message.body().length();
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

  protected AttributesExtractor<Collection<Message>, Void> getAttributesExtractor() {
    return MessagingAttributesExtractor.create(
        getMessageingAttributesGetter(), MessageOperation.PROCESS);
  }

  protected SpanLinksExtractor<Collection<Message>> getSpanLinksExtractor() {
    return (spanLinks, parentContext, request) -> {
      TextMapPropagator messagingPropagator = openTelemetry.getPropagators().getTextMapPropagator();

      for (Message message : request) {
        Map<String, SdkPojo> messageAtributes = SqsMessageAccess.getMessageAttributes(message);

        Context context =
            SqsParentContext.ofMessageAttributes(messageAtributes, messagingPropagator);

        if (context == Context.root()) {
          context = SqsParentContext.ofSystemAttributes(SqsMessageAccess.getAttributes(message));
        }

        SpanContext messageSpanCtx = Span.fromContext(context).getSpanContext();

        if (messageSpanCtx.isValid()) {
          spanLinks.addLink(messageSpanCtx);
        }
      }
    };
  }
}
