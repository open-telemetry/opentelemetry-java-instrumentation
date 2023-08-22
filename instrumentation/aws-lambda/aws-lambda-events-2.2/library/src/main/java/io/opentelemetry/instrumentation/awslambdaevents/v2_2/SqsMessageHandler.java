/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.messagehandler.MessageHandler;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public abstract class SqsMessageHandler extends MessageHandler<SQSEvent.SQSMessage> {
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";
  static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  private final OpenTelemetry openTelemetry;
  private final String destination;
  private SpanKindExtractor<Collection<SQSEvent.SQSMessage>> spanKindExtractor;
  private SpanNameExtractor<Collection<SQSEvent.SQSMessage>> spanNameExtractor;

  public SqsMessageHandler(OpenTelemetry openTelemetry, String destination) {
    this.openTelemetry = openTelemetry;
    this.destination = destination;
    this.spanKindExtractor = SpanKindExtractor.alwaysConsumer();
    spanNameExtractor = e -> destination + " process";
  }

  public void setSpanNameExtactor(SpanNameExtractor<Collection<SQSEvent.SQSMessage>> spanNameExtractor) {
    this.spanNameExtractor = spanNameExtractor;
  }

  @Override
  protected Instrumenter<Collection<SQSEvent.SQSMessage>, Void> getMessageInstrumenter() {
    return Instrumenter.<Collection<SQSEvent.SQSMessage>, Void>builder(
            openTelemetry, "io.opentelemetry.aws-lambda-events-2.2", spanNameExtractor)
        .addAttributesExtractor(getMessageOperationAttributeExtractor())
        .addSpanLinksExtractor(getSpanLinksExtractor())
        .buildInstrumenter(spanKindExtractor);
  }

  public void setSpanKindExtractor(SpanKindExtractor<Collection<SQSEvent.SQSMessage>> spanKindExtractor) {
    this.spanKindExtractor = spanKindExtractor;
  }

  protected MessagingAttributesGetter<Collection<SQSEvent.SQSMessage>, Void>
      getMessageingAttributesGetter() {
    String destination = this.destination;

    return new MessagingAttributesGetter<Collection<SQSEvent.SQSMessage>, Void>() {
      @Nullable
      @Override
      public String getSystem(Collection<SQSEvent.SQSMessage> v) {
        return "AmazonSQS";
      }

      @Nullable
      @Override
      @SuppressWarnings({"deprecation"}) // Inheriting from interface
      public String getDestinationKind(Collection<SQSEvent.SQSMessage> v) {
        return null;
      }

      @Nullable
      @Override
      public String getDestination(Collection<SQSEvent.SQSMessage> v) {
        return destination;
      }

      @Override
      public boolean isTemporaryDestination(Collection<SQSEvent.SQSMessage> v) {
        return false;
      }

      @Nullable
      @Override
      public String getConversationId(Collection<SQSEvent.SQSMessage> v) {
        return null;
      }

      @Nullable
      @Override
      public Long getMessagePayloadSize(Collection<SQSEvent.SQSMessage> v) {
        long total = 0;

        for (SQSEvent.SQSMessage message : v) {
          total += getPayloadSize(message);
        }

        return total;
      }

      @Nullable
      @Override
      public Long getMessagePayloadCompressedSize(Collection<SQSEvent.SQSMessage> v) {
        return null;
      }

      @Nullable
      @Override
      public String getMessageId(Collection<SQSEvent.SQSMessage> request, Void v) {
        return null;
      }
    };
  }

  protected AttributesExtractor<Collection<SQSEvent.SQSMessage>, Void> getMessageOperationAttributeExtractor() {
    return MessagingAttributesExtractor.create(
        getMessageingAttributesGetter(), MessageOperation.PROCESS);
  }

  protected SpanLinksExtractor<Collection<SQSEvent.SQSMessage>> getSpanLinksExtractor() {
    return (spanLinks, parentContext, request) -> {
      for (SQSEvent.SQSMessage message : request) {
        SpanContext messageSpanCtx = getUpstreamContext(openTelemetry, message);

        if (messageSpanCtx!= null && messageSpanCtx.isValid()) {
          spanLinks.addLink(messageSpanCtx);
        }
      }
    };
  }

  public int getPayloadSize(SQSEvent.SQSMessage message) {
    return message.getBody().length();
  }

  public SpanContext getUpstreamContext(OpenTelemetry openTelemetry, SQSEvent.SQSMessage message) {
    String parentHeader = null;

    if (message.getAttributes() != null) {
      parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
    }

    if (parentHeader == null &&
        message.getMessageAttributes() != null)
    {
      // We need to do a case-insensitive search
      for (Map.Entry<String, SQSEvent.MessageAttribute> entry: message.getMessageAttributes().entrySet()) {
        if (entry.getKey().equalsIgnoreCase(AWS_TRACE_HEADER_PROPAGATOR_KEY)) {
          parentHeader = entry.getValue().getStringValue();
          break;
        }
      }
    }

    if (parentHeader != null) {
      Context xrayContext =
          AwsXrayPropagator.getInstance()
              .extract(
                  Context.root(),
                  Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
                  MapGetter.INSTANCE);

      return Span.fromContext(xrayContext).getSpanContext();
    }

    return null;
  }

  private enum MapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s.toLowerCase(Locale.ROOT));
    }
  }
}
