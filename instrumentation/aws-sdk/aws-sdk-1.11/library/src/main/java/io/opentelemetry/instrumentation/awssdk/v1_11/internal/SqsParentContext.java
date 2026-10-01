/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static java.util.Collections.singletonMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import java.util.Map;
import javax.annotation.Nullable;

final class SqsParentContext {

  static final String AWS_TRACE_SYSTEM_ATTRIBUTE = "AWSTraceHeader";
  private static final String AWS_TRACE_HEADER = "X-Amzn-Trace-Id";

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

  static Context ofSystemAttributes(Map<String, String> systemAttributes) {
    return ofSystemAttributes(Context.root(), systemAttributes);
  }

  static Context ofSystemAttributes(Context parentContext, Map<String, String> systemAttributes) {
    String traceHeader = systemAttributes.get(AWS_TRACE_SYSTEM_ATTRIBUTE);
    return ofTraceHeader(parentContext, traceHeader);
  }

  static Context ofTraceHeader(@Nullable String traceHeader) {
    return ofTraceHeader(Context.root(), traceHeader);
  }

  private static Context ofTraceHeader(Context parentContext, @Nullable String traceHeader) {
    return AwsXrayPropagator.getInstance()
        .extract(parentContext, singletonMap(AWS_TRACE_HEADER, traceHeader), new MapGetter());
  }

  static String toTraceHeader(Context context) {
    String[] traceHeader = new String[1];
    AwsXrayPropagator.getInstance()
        .inject(
            context,
            traceHeader,
            (carrier, key, value) -> {
              if (AWS_TRACE_HEADER.equals(key)) {
                carrier[0] = value;
              }
            });
    return traceHeader[0];
  }

  private SqsParentContext() {}
}
