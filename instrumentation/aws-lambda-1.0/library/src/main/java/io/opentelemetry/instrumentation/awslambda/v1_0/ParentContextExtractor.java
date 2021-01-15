/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.instrumentation.awslambda.v1_0.MapUtils.lowercaseMap;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extension.trace.propagation.AwsXrayPropagator;
import java.util.Collections;
import java.util.Map;

public class ParentContextExtractor {

  static Context fromHttpHeaders(Map<String, String> headers) {
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(
            io.opentelemetry.context.Context.current(), lowercaseMap(headers), MapGetter.INSTANCE);
  }

  // lower-case map getter used for extraction
  static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  static Context fromXRayHeader(String parentHeader) {
    return AwsXrayPropagator.getInstance()
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
      return map.get(s.toLowerCase());
    }
  }
}
