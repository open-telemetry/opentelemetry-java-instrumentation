/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.messagehandler;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

public abstract class SqsBatchMessageHandler extends BatchMessageHandler<SQSEvent.SQSMessage> {
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";

  // lower-case map getter used for extraction
  static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  public SqsBatchMessageHandler(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  public SqsBatchMessageHandler(OpenTelemetry openTelemetry, String messageOperation) {
    super(openTelemetry, messageOperation);
  }

  public SqsBatchMessageHandler(
      OpenTelemetry openTelemetry,
      String messageOperation,
      SpanNameExtractor<Collection<SQSEvent.SQSMessage>> spanNameExtractor) {
    super(openTelemetry, messageOperation, spanNameExtractor);
  }

  @Override
  protected void setup() {
    messageInstrumenter = Instrumenter
        .builder(openTelemetry, "io.opentelemetry.message.handler", spanNameExtractor)
        .setInstrumentationVersion("1.0")
        .addAttributesExtractor(getGenericAttributesExtractor())
        .addAttributesExtractor(getAttributesExtractor())
        .addSpanLinksExtractor(getSpanLinksExtractor())
        .buildInstrumenter();
  }

  protected AttributesExtractor<Collection<SQSEvent.SQSMessage>, Void> getAttributesExtractor() {
    return new AttributesExtractor<Collection<SQSEvent.SQSMessage>, Void>() {

      @Override
      public void onStart(
          AttributesBuilder attributes,
          Context parentContext,
          Collection<SQSEvent.SQSMessage> messages) {
        attributes.put(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS");
      }

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          Collection<SQSEvent.SQSMessage> messages,
          @Nullable Void unused,
          @Nullable Throwable error) {}
    };
  }

  protected SpanLinksExtractor<Collection<SQSEvent.SQSMessage>> getSpanLinksExtractor() {
    return (spanLinks, parentContext, sqsMessages) -> {
      for (SQSEvent.SQSMessage message : sqsMessages) {
        String parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
        if (parentHeader != null) {
          Context xrayContext =
              openTelemetry
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
