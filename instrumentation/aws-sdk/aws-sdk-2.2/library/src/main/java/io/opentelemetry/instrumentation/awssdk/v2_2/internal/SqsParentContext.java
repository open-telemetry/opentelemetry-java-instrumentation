/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Collections.singletonMap;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsParentContext {

  static final String AWS_TRACE_SYSTEM_ATTRIBUTE = "AWSTraceHeader";
  private static final String AWS_TRACE_HEADER = "X-Amzn-Trace-Id";

  enum StringMapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(@Nullable Map<String, String> map, String s) {
      if (map == null) {
        return null;
      }
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
    public String get(@Nullable Map<String, MessageAttributeValue> map, String s) {
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

  static Context ofMessageAttributes(
      Map<String, MessageAttributeValue> messageAttributes, TextMapPropagator propagator) {
    return ofMessageAttributes(Context.root(), messageAttributes, propagator);
  }

  static Context ofMessageAttributes(
      Context parentContext,
      Map<String, MessageAttributeValue> messageAttributes,
      TextMapPropagator propagator) {
    return propagator.extract(
        parentContext, messageAttributes, MessageAttributeValueMapGetter.INSTANCE);
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
        .extract(
            parentContext, singletonMap(AWS_TRACE_HEADER, traceHeader), StringMapGetter.INSTANCE);
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

  public static Context ofMessage(SqsMessage message, TracingExecutionInterceptor config) {
    return ofMessage(Context.root(), message, config);
  }

  public static Context ofMessage(
      Context parentContext, SqsMessage message, TracingExecutionInterceptor config) {
    return ofMessage(
        parentContext, message, config.getMessagingPropagator(), config.shouldUseXrayPropagator());
  }

  static Context ofMessage(
      SqsMessage message, TextMapPropagator messagingPropagator, boolean shouldUseXrayPropagator) {
    return ofMessage(Context.root(), message, messagingPropagator, shouldUseXrayPropagator);
  }

  static Context ofMessage(
      Context parentContext,
      SqsMessage message,
      TextMapPropagator messagingPropagator,
      boolean shouldUseXrayPropagator) {
    // extract against a context without the ambient span, so that a span in the extracted context
    // is known to have come from the message instead of being inherited from parentContext. an
    // ambient span is not a creation context and must not suppress the X-Ray fallback
    Span ambientSpan = Span.fromContext(parentContext);
    Context extractedContext = parentContext.with(Span.getInvalid());

    if (messagingPropagator != null) {
      extractedContext =
          ofMessageAttributes(extractedContext, message.messageAttributes(), messagingPropagator);
    }

    if (shouldUseXrayPropagator && !hasSpan(extractedContext)) {
      extractedContext = ofSystemAttributes(extractedContext, message.attributesAsStrings());
    }

    // the message did not carry a creation context, restore the ambient span
    if (!hasSpan(extractedContext)) {
      extractedContext = extractedContext.with(ambientSpan);
    }

    return extractedContext;
  }

  private static boolean hasSpan(Context context) {
    return Span.fromContext(context).getSpanContext().isValid();
  }

  private SqsParentContext() {}
}
