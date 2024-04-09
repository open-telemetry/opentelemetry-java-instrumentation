/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import javax.annotation.Nullable;

class SqsMessageAttributesExtractor implements AttributesExtractor<SQSMessage, Void> {
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, SQSMessage message) {
    attributes.put(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "AmazonSQS");
    attributes.put(MessagingIncubatingAttributes.MESSAGING_OPERATION, "process");
    attributes.put(MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID, message.getMessageId());
    attributes.put(
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, message.getEventSource());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SQSMessage message,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
