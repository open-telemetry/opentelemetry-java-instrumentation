/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.ParentContextExtractor;

class SqsMessageSpanLinksExtractor implements SpanLinksExtractor<SQSMessage> {
  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, SQSMessage message) {
    String parentHeader = message.getAttributes().get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
    if (parentHeader != null) {
      SpanContext parentCtx =
          Span.fromContext(ParentContextExtractor.fromXrayHeader(parentHeader)).getSpanContext();
      if (parentCtx.isValid()) {
        spanLinks.addLink(parentCtx);
      }
    }
  }
}
