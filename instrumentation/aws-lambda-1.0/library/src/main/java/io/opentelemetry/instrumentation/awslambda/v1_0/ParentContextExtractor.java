/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.instrumentation.awslambda.v1_0.MapUtils.lowercaseMap;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.util.Collections;
import java.util.Map;

public class ParentContextExtractor {

  private static final String AWS_TRACE_HEADER_ENV_KEY = "_X_AMZN_TRACE_ID";

  static Context extract(Map<String, String> headers, BaseTracer tracer) {
    Context parentContext = null;
    String parentTraceHeader = System.getenv(AWS_TRACE_HEADER_ENV_KEY);
    if (parentTraceHeader != null) {
      parentContext = fromXRayHeader(parentTraceHeader);
    }
    if (!isValidAndSampled(parentContext)) {
      // try http
      parentContext = fromHttpHeaders(headers, tracer);
    }
    return parentContext;
  }

  private static boolean isValidAndSampled(Context context) {
    if (context == null) {
      return false;
    }
    Span parentSpan = Span.fromContext(context);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    return (parentSpanContext.isValid() && parentSpanContext.isSampled());
  }

  private static Context fromHttpHeaders(Map<String, String> headers, BaseTracer tracer) {
    return tracer.extract(lowercaseMap(headers), MapGetter.INSTANCE);
  }

  // lower-case map getter used for extraction
  static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  static Context fromXRayHeader(String parentHeader) {
    return AwsXrayPropagator.getInstance()
        .extract(
            // see BaseTracer#extract() on why we're using root() here
            Context.root(),
            Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
            MapGetter.INSTANCE);
  }

  private static class MapGetter implements TextMapGetter<Map<String, String>> {

    private static final MapGetter INSTANCE = new MapGetter();

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s.toLowerCase());
    }
  }
}
