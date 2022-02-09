/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;

class SqsEventSpanLinksExtractor implements SpanLinksExtractor<SQSEvent> {
  private static final SqsMessageSpanLinksExtractor messageSpanLinksExtractor =
      new SqsMessageSpanLinksExtractor();

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, SQSEvent event) {
    for (SQSEvent.SQSMessage message : event.getRecords()) {
      messageSpanLinksExtractor.extract(spanLinks, parentContext, message);
    }
  }
}
