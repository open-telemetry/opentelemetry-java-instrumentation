/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.v1_0;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_FLAGS;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.logback.v1_0.internal.UnionMap;
import java.util.HashMap;
import java.util.Map;

public final class MdcPropertyMapHelper {

  public static boolean isInstrumented(Map<String, String> mdcPropertyMap) {
    // Assume already instrumented event if traceId is present.
    return mdcPropertyMap != null && mdcPropertyMap.containsKey(TRACE_ID);
  }

  public static Map<String, String> instrument(
      Map<String, String> mcdPropertyMap, SpanContext spanContext) {
    Map<String, String> propsToAdd = new HashMap<>();
    propsToAdd.put(TRACE_ID, spanContext.getTraceId());
    propsToAdd.put(SPAN_ID, spanContext.getSpanId());
    propsToAdd.put(TRACE_FLAGS, spanContext.getTraceFlags().asHex());

    if (mcdPropertyMap == null) {
      return propsToAdd;
    } else {
      return new UnionMap<>(mcdPropertyMap, propsToAdd);
    }
  }

  private MdcPropertyMapHelper() {}

}
