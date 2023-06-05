/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import java.util.Collections;
import java.util.Map;
import software.amazon.awssdk.core.SdkPojo;

final class SqsParentContext {

  enum StringMapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s);
    }
  }

  enum MessageAttributeValueMapGetter implements TextMapGetter<Map<String, SdkPojo>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, SdkPojo> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, SdkPojo> map, String s) {
      if (map == null) {
        return null;
      }
      SdkPojo value = map.get(s);
      if (value == null) {
        return null;
      }
      return SqsMessageAttributeValueAccess.getStringValue(value);
    }
  }

  static final String AWS_TRACE_SYSTEM_ATTRIBUTE = "AWSTraceHeader";

  static Context ofMessageAttributes(
      Map<String, SdkPojo> messageAttributes, TextMapPropagator propagator) {
    return propagator.extract(
        Context.root(), messageAttributes, MessageAttributeValueMapGetter.INSTANCE);
  }

  static Context ofSystemAttributes(Map<String, String> systemAttributes) {
    String traceHeader = systemAttributes.get(AWS_TRACE_SYSTEM_ATTRIBUTE);
    return AwsXrayPropagator.getInstance()
        .extract(
            Context.root(),
            Collections.singletonMap("X-Amzn-Trace-Id", traceHeader),
            StringMapGetter.INSTANCE);
  }

  private SqsParentContext() {}
}
