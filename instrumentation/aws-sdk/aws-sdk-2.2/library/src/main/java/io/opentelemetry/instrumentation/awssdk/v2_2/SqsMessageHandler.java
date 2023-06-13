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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.sqs.model.Message;

public abstract class SqsMessageHandler extends MessageHandler<Message> {
  private static final Logger logger = Logger.getLogger(SqsMessageHandler.class.getName());

  private final OpenTelemetry openTelemetry;
  private final MessageOperation messageOperation;
  private final String destination;

  public SqsMessageHandler(
      OpenTelemetry openTelemetry, String destination, MessageOperation messageOperation) {
    this.openTelemetry = openTelemetry;
    this.messageOperation = messageOperation;
    this.destination = destination;
  }

  @Override
  protected Instrumenter<Collection<Message>, Void> getMessageInstrumenter() {
    return Instrumenter.<Collection<Message>, Void>builder(
            openTelemetry, "io.opentelemetry.aws-sdk-2.2", getSpanNameExtractor())
        .addAttributesExtractor(getAttributesExtractor())
        .addSpanLinksExtractor(getSpanLinksExtractor())
        .buildInstrumenter(getSpanKindExtractor());
  }

  protected SpanNameExtractor<Collection<Message>> getSpanNameExtractor() {
    return MessagingSpanNameExtractor.create(getMessageingAttributesGetter(), messageOperation);
  }

  private MessagingAttributesGetter<Collection<Message>, Void> getMessageingAttributesGetter() {
    String destination = this.destination;

    return new MessagingAttributesGetter<Collection<Message>, Void>() {
      @Nullable
      @Override
      public String getSystem(Collection<Message> messages) {
        return "AmazonSQS";
      }

      @Nullable
      @Override
      public String getDestinationKind(Collection<Message> messages) {
        return null;
      }

      @Nullable
      @Override
      public String getDestination(Collection<Message> messages) {
        return destination;
      }

      @Override
      public boolean isTemporaryDestination(Collection<Message> messages) {
        return false;
      }

      @Nullable
      @Override
      public String getConversationId(Collection<Message> messages) {
        return null;
      }

      @Nullable
      @Override
      public Long getMessagePayloadSize(Collection<Message> messages) {
        long total = 0;

        for (Message message : messages) {
          if (message.body() != null) {
            total += message.body().length();
          }
        }

        return total;
      }

      @Nullable
      @Override
      public Long getMessagePayloadCompressedSize(Collection<Message> messages) {
        return null;
      }

      @Nullable
      @Override
      public String getMessageId(Collection<Message> messages, @Nullable Void unused) {
        return null;
      }
    };
  }

  protected SpanKindExtractor<Collection<Message>> getSpanKindExtractor() {
    if (messageOperation == MessageOperation.RECEIVE
        || messageOperation == MessageOperation.PROCESS) {
      return SpanKindExtractor.alwaysConsumer();
    } else if (messageOperation == MessageOperation.SEND) {
      return SpanKindExtractor.alwaysProducer();
    } else {
      logger.log(
          Level.WARNING, "Unknown Messaging Operation {0}", new Object[] {messageOperation.name()});
      return SpanKindExtractor.alwaysConsumer();
    }
  }

  protected AttributesExtractor<Collection<Message>, Void> getAttributesExtractor() {
    return MessagingAttributesExtractor.create(getMessageingAttributesGetter(), messageOperation);
  }

  protected SpanLinksExtractor<Collection<Message>> getSpanLinksExtractor() {
    TextMapPropagator messagingPropagator = openTelemetry.getPropagators().getTextMapPropagator();

    return (spanLinks, parentContext, sqsMessages) -> {
      for (Message message : sqsMessages) {
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
