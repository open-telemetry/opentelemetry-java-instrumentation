/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.extension.aws.AwsXrayPropagator;
import java.util.Collections;
import java.util.Map;

class SqsParentContext {

  private static class MapGetter implements TextMapGetter<Map<String, String>> {

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

  static final String AWS_TRACE_SYSTEM_ATTRIBUTE = "AWSTraceHeader";

  static Context ofSystemAttributes(Map<String, String> systemAttributes) {
    String traceHeader = systemAttributes.get(AWS_TRACE_SYSTEM_ATTRIBUTE);
    return AwsXrayPropagator.getInstance()
        .extract(
            Context.current(),
            Collections.singletonMap("X-Amzn-Trace-Id", traceHeader),
            MapGetter.INSTANCE);
  }
}
