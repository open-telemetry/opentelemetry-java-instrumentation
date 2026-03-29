/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.util.Collections.singletonMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import java.util.Map;
import javax.annotation.Nullable;

final class SqsParentContext {

  private static class MapGetter implements TextMapGetter<Map<String, String>> {

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    @Nullable
    public String get(@Nullable Map<String, String> map, String s) {
      if (map == null) {
        return null;
      }
      return map.get(s);
    }
  }

  static final String AWS_TRACE_SYSTEM_ATTRIBUTE = "AWSTraceHeader";

  static Context ofSystemAttributes(Map<String, String> systemAttributes) {
    String traceHeader = systemAttributes.get(AWS_TRACE_SYSTEM_ATTRIBUTE);
    return AwsXrayPropagator.getInstance()
        .extract(Context.root(), singletonMap("X-Amzn-Trace-Id", traceHeader), new MapGetter());
  }

  private SqsParentContext() {}
}
