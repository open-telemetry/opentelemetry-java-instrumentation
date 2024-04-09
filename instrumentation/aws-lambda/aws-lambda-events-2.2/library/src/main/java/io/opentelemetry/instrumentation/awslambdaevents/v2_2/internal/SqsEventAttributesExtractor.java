/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import javax.annotation.Nullable;

class SqsEventAttributesExtractor implements AttributesExtractor<SQSEvent, Void> {
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, SQSEvent event) {
    attributes.put(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "AmazonSQS");
    attributes.put(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process");
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SQSEvent event,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
