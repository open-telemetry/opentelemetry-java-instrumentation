/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
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
import io.opentelemetry.instrumentation.awssdk.v2_2.SqsMessageAccess;
import io.opentelemetry.instrumentation.awssdk.v2_2.SqsParentContext;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.sqs.model.Message;

public abstract class SqsMessageHandler extends MessageHandler<Message> {
  private static final Logger logger = Logger.getLogger(SqsMessageHandler.class.getName());

  public SqsMessageHandler(
      OpenTelemetry openTelemetry, SpanNameExtractor<Collection<Message>> spanNameExtractor) {
    super(openTelemetry, spanNameExtractor);
  }

  public SqsMessageHandler(
      OpenTelemetry openTelemetry,
      SpanNameExtractor<Collection<Message>> spanNameExtractor,
      MessageOperation messageOperation) {
    super(openTelemetry, spanNameExtractor, messageOperation);
  }

  @Override
  protected void setup() {
    messageInstrumenter =
        Instrumenter.<Collection<Message>, Void>builder(
                getOpenTelemetry(), "io.opentelemetry.message-handler", getSpanNameExtractor())
            .setInstrumentationVersion("1.0")
            .addAttributesExtractor(getGenericAttributesExtractor())
            .addAttributesExtractor(getAttributesExtractor())
            .addSpanLinksExtractor(getSpanLinksExtractor())
            .buildInstrumenter(getSpanKindExtractor());
  }

  protected SpanKindExtractor<Collection<Message>> getSpanKindExtractor() {
    MessageOperation messageOperation = getMessagingOperation();

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
    return new AttributesExtractor<Collection<Message>, Void>() {

      @Override
      public void onStart(
          AttributesBuilder attributes, Context parentContext, Collection<Message> messages) {
        attributes.put(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
      }

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          Collection<Message> messages,
          @Nullable Void unused,
          @Nullable Throwable error) {}
    };
  }

  protected SpanLinksExtractor<Collection<Message>> getSpanLinksExtractor() {
    TextMapPropagator messagingPropagator =
        getOpenTelemetry().getPropagators().getTextMapPropagator();

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
