/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;

class SqsEventSpanLinksExtractor implements SpanLinksExtractor<SQSEvent> {
  private final SqsMessageSpanLinksExtractor messageSpanLinksExtractor;

  SqsEventSpanLinksExtractor(OpenTelemetry openTelemetry) {
    messageSpanLinksExtractor = new SqsMessageSpanLinksExtractor(openTelemetry);
  }

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, SQSEvent event) {
    for (SQSEvent.SQSMessage message : event.getRecords()) {
      messageSpanLinksExtractor.extract(spanLinks, parentContext, message);
    }
  }
}
