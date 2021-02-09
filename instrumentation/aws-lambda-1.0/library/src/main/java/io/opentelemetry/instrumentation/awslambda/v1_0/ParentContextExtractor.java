/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.instrumentation.awslambda.v1_0.MapUtils.lowercaseMap;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.util.Collections;
import java.util.Map;

public class ParentContextExtractor {

  private static final String AWS_TRACE_HEADER_ENV_KEY = "_X_AMZN_TRACE_ID";

  static Context extract(Map<String, String> headers) {
    Context parentContext = null;
    String parentTraceHeader = System.getenv(AWS_TRACE_HEADER_ENV_KEY);
    if (parentTraceHeader != null) {
      parentContext = ParentContextExtractor.fromXRayHeader(parentTraceHeader);
    }
    if (!isValid(parentContext)) {
      // try http
      parentContext = ParentContextExtractor.fromHttpHeaders(headers);
    }
    return parentContext;
  }

  private static boolean isValid(Context context) {
    if (context == null) {
      return false;
    }
    Span parentSpan = Span.fromContext(context);
    SpanContext parentSpanContext = parentSpan.getSpanContext();
    return parentSpanContext.isValid();
  }

  static Context fromHttpHeaders(Map<String, String> headers) {
    return BaseTracer.extractWithGlobalPropagators(lowercaseMap(headers), MapGetter.INSTANCE);
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

  private static class MapGetter implements TextMapPropagator.Getter<Map<String, String>> {

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
