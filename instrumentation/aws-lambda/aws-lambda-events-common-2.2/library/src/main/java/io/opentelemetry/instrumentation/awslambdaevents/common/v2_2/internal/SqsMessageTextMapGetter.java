/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static java.util.Collections.singletonList;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;
import javax.annotation.Nullable;

final class SqsMessageTextMapGetter implements TextMapGetter<SQSMessage> {
  static final SqsMessageTextMapGetter INSTANCE = new SqsMessageTextMapGetter();

  private static final String AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY = "AWSTraceHeader";
  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  @Override
  public Iterable<String> keys(SQSMessage message) {
    return singletonList(AWS_TRACE_HEADER_PROPAGATOR_KEY);
  }

  @Nullable
  @Override
  public String get(@Nullable SQSMessage message, String key) {
    if (message == null || !AWS_TRACE_HEADER_PROPAGATOR_KEY.equalsIgnoreCase(key)) {
      return null;
    }
    Map<String, String> attributes = message.getAttributes();
    return attributes == null ? null : attributes.get(AWS_TRACE_HEADER_SQS_ATTRIBUTE_KEY);
  }

  private SqsMessageTextMapGetter() {}
}
