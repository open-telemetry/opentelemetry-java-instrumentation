/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import java.util.List;

class SqsEventSpanLinksExtractor implements SpanLinksExtractor<SQSEvent> {

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, SQSEvent event) {
    List<SQSMessage> records = event.getRecords();
    if (records == null) {
      return;
    }

    SqsEventRecordAttributes attributes = SqsEventRecordAttributes.create(event);
    for (SQSMessage record : records) {
      SpanContext creationSpanContext = creationSpanContext(record);
      Attributes linkAttributes = attributes.getLinkAttributes(record);
      if (creationSpanContext.isValid() || !linkAttributes.isEmpty()) {
        spanLinks.addLink(creationSpanContext, linkAttributes);
      }
    }
  }

  private static SpanContext creationSpanContext(SQSMessage record) {
    Context creationContext =
        AwsXrayPropagator.getInstance()
            .extract(Context.root(), record, SqsMessageTextMapGetter.INSTANCE);
    return Span.fromContext(creationContext).getSpanContext();
  }
}
