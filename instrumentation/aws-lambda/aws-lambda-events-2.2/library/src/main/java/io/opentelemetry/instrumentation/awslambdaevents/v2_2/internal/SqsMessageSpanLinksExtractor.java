/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import java.util.Locale;
import java.util.Map;

class SqsMessageSpanLinksExtractor implements SpanLinksExtractor<SQSMessage> {

  private final OpenTelemetry openTelemetry;

  SqsMessageSpanLinksExtractor(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void extract(SpanLinksBuilder spanLinks, Context parentContext, SQSMessage message) {
    Context parentCtx =
        openTelemetry
            .getPropagators()
            .getTextMapPropagator()
            .extract(Context.root(), message.getAttributes(), MapGetter.INSTANCE);
    SpanContext parent = Span.fromContext(parentCtx).getSpanContext();
    if (parent.isValid()) {
      spanLinks.addLink(parent);
    }
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
