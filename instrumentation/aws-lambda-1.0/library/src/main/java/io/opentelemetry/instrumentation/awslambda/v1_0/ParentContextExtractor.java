/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.serverless.proxy.model.Headers;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Collections;
import java.util.Map;

public class ParentContextExtractor {

  static Context fromHttpHeaders(Headers headers) {

    return OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .extract(io.opentelemetry.context.Context.current(), headers, HeadersGetter.INSTANCE);
  }

  private static class HeadersGetter implements TextMapPropagator.Getter<Headers> {

    private static final HeadersGetter INSTANCE = new HeadersGetter();

    @Override
    public Iterable<String> keys(Headers map) {
      return map.keySet();
    }

    @Override
    public String get(Headers headers, String s) {
      return headers.getFirst(s);
    }
  }

  static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "X-Amzn-Trace-Id";

  static Context fromXRayHeader(String parentHeader) {
    return OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .extract(
            Context.current(),
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
      return map.get(s);
    }
  }
}
