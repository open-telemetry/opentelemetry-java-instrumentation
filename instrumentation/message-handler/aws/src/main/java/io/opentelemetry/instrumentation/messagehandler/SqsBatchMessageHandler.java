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
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.services.sqs.model.Message;

public abstract class SqsBatchMessageHandler extends BatchMessageHandler<Message> {
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";

  // lower-case map getter used for extraction
  static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  public SqsBatchMessageHandler(
      OpenTelemetry openTelemetry, SpanNameExtractor<Collection<Message>> spanNameExtractor) {
    super(openTelemetry, spanNameExtractor);
  }

  public SqsBatchMessageHandler(
      OpenTelemetry openTelemetry,
      SpanNameExtractor<Collection<Message>> spanNameExtractor,
      String messageOperation) {
    super(openTelemetry, spanNameExtractor, messageOperation);
  }

  @Override
  protected void setup() {
    messageInstrumenter =
        Instrumenter.<Collection<Message>, Void>builder(
                getOpenTelemetry(), "io.opentelemetry.message.handler", getSpanNameExtractor())
            .setInstrumentationVersion("1.0")
            .addAttributesExtractor(getGenericAttributesExtractor())
            .addAttributesExtractor(getAttributesExtractor())
            .addSpanLinksExtractor(getSpanLinksExtractor())
            .buildInstrumenter();
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
    return (spanLinks, parentContext, sqsMessages) -> {
      for (Message message : sqsMessages) {
        String parentHeader =
            message.messageAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY).stringValue();
        if (parentHeader != null) {
          Context xrayContext =
              getOpenTelemetry()
                  .getPropagators()
                  .getTextMapPropagator()
                  .extract(
                      Context.root(), // We don't want the ambient context.
                      Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
                      MapGetter.INSTANCE);
          SpanContext messageSpanCtx = Span.fromContext(xrayContext).getSpanContext();
          if (messageSpanCtx.isValid()) {
            spanLinks.addLink(messageSpanCtx);
          }
        }
      }
    };
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
