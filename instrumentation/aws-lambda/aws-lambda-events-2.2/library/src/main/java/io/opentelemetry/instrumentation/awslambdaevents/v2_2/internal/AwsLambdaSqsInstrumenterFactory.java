/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AwsLambdaSqsInstrumenterFactory {

  public static Instrumenter<SQSEvent, Void> forEvent(OpenTelemetry openTelemetry) {
    return Instrumenter.<SQSEvent, Void>builder(
            openTelemetry,
            "io.opentelemetry.aws-lambda-events-2.2",
            AwsLambdaSqsInstrumenterFactory::spanName)
        .addAttributesExtractors(new SqsEventAttributesExtractor())
        .addSpanLinksExtractor(new SqsEventSpanLinksExtractor())
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<SQSMessage, Void> forMessage(OpenTelemetry openTelemetry) {
    return Instrumenter.<SQSMessage, Void>builder(
            openTelemetry,
            "io.opentelemetry.aws-lambda-events-2.2",
            message -> message.getEventSource() + " process")
        .addAttributesExtractors(new SqsMessageAttributesExtractor())
        .addSpanLinksExtractor(new SqsMessageSpanLinksExtractor())
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static String spanName(SQSEvent event) {
    String source = "multiple_sources";
    if (!event.getRecords().isEmpty()) {
      String messageSource = event.getRecords().get(0).getEventSource();
      for (int i = 1; i < event.getRecords().size(); i++) {
        SQSMessage message = event.getRecords().get(i);
        if (!message.getEventSource().equals(messageSource)) {
          messageSource = null;
          break;
        }
      }
      if (messageSource != null) {
        source = messageSource;
      }
    }

    return source + " process";
  }
}
