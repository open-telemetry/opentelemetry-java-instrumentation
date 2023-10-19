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
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsLambdaSqsInstrumenterFactory {

  public static Instrumenter<SQSEvent, Void> forEvent(OpenTelemetry openTelemetry) {
    return Instrumenter.<SQSEvent, Void>builder(
            openTelemetry,
            "io.opentelemetry.aws-lambda-events-2.2",
            AwsLambdaSqsInstrumenterFactory::spanName)
        .addAttributesExtractor(new SqsEventAttributesExtractor())
        .addSpanLinksExtractor(new SqsEventSpanLinksExtractor())
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<SQSMessage, Void> forMessage(OpenTelemetry openTelemetry) {
    return Instrumenter.<SQSMessage, Void>builder(
            openTelemetry,
            "io.opentelemetry.aws-lambda-events-2.2",
            message -> message.getEventSource() + " process")
        .addAttributesExtractor(new SqsMessageAttributesExtractor())
        .addSpanLinksExtractor(new SqsMessageSpanLinksExtractor())
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static String spanName(SQSEvent event) {
    String source = "multiple_sources";
    List<SQSMessage> records = event.getRecords();
    if (records != null && !records.isEmpty()) {
      String messageSource = records.get(0).getEventSource();
      for (int i = 1; i < records.size(); i++) {
        SQSMessage message = records.get(i);
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

  private AwsLambdaSqsInstrumenterFactory() {}
}
