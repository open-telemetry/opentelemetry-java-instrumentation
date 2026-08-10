/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;

class SqsEventSpanLinksExtractor implements SpanLinksExtractor<SQSEvent> {

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, SQSEvent event) {
    for (SQSEvent.SQSMessage message : event.getRecords()) {
      Context creationContext =
          AwsXrayPropagator.getInstance()
              .extract(Context.root(), message, SqsMessageTextMapGetter.INSTANCE);
      SpanContext creationSpanContext = Span.fromContext(creationContext).getSpanContext();
      if (creationSpanContext.isValid()) {
        spanLinks.addLink(creationSpanContext);
      }
    }
  }
}
