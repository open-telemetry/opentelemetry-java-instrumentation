/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ParentContextExtractor {

  static Context fromHttpHeaders(Map<String, String> headers) {
    return OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .extract(
            io.opentelemetry.context.Context.current(), lowercaseMap(headers), MapGetter.INSTANCE);
  }

  private static Map<String, String> lowercaseMap(Map<String, String> source) {
    return source.entrySet().stream()
        .collect(
            Collectors.toMap(
                e -> e.getKey() == null ? null : e.getKey().toLowerCase(), Entry::getValue));
  }

  static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "X-Amzn-Trace-Id";

  static Context fromXRayHeader(String parentHeader) {
    return OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .extract(
            Context.current(),
            Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY.toLowerCase(), parentHeader),
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
