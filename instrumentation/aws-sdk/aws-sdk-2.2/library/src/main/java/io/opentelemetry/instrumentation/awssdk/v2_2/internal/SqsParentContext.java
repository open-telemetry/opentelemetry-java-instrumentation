/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.Collections;
import java.util.Map;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsParentContext {

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

  enum MessageAttributeValueMapGetter implements TextMapGetter<Map<String, MessageAttributeValue>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, MessageAttributeValue> map) {
      return map.keySet();
    }

    @Override
    @NoMuzzle
    public String get(Map<String, MessageAttributeValue> map, String s) {
      if (map == null) {
        return null;
      }
      MessageAttributeValue value = map.get(s);
      if (value == null) {
        return null;
      }
      return value.stringValue();
    }
  }

  static final String AWS_TRACE_SYSTEM_ATTRIBUTE = "AWSTraceHeader";

  static Context ofMessageAttributes(
      Map<String, MessageAttributeValue> messageAttributes, TextMapPropagator propagator) {
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

  public static Context ofMessage(SqsMessage message, TracingExecutionInterceptor config) {
    return ofMessage(message, config.getMessagingPropagator(), config.shouldUseXrayPropagator());
  }

  static Context ofMessage(
      SqsMessage message, TextMapPropagator messagingPropagator, boolean shouldUseXrayPropagator) {
    io.opentelemetry.context.Context parentContext = io.opentelemetry.context.Context.root();

    if (messagingPropagator != null) {
      parentContext = ofMessageAttributes(message.messageAttributes(), messagingPropagator);
    }

    if (shouldUseXrayPropagator && parentContext == io.opentelemetry.context.Context.root()) {
      parentContext = ofSystemAttributes(message.attributesAsStrings());
    }

    return parentContext;
  }

  private SqsParentContext() {}
}
