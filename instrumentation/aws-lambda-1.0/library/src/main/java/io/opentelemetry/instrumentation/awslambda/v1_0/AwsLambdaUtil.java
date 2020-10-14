/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import java.util.Collections;
import java.util.Map;

final class AwsLambdaUtil {
  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "X-Amzn-Trace-Id";

  static Context extractParent(String parentHeader) {
    return OpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(
            Context.current(),
            Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentHeader),
            MapGetter.INSTANCE);
  }

  private static class MapGetter implements Getter<Map<String, String>> {

    private static final MapGetter INSTANCE = new MapGetter();

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s);
    }
  }

  private AwsLambdaUtil() {}
}
