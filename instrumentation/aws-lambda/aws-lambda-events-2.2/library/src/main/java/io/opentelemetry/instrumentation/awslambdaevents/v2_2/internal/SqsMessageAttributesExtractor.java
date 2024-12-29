/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class SqsMessageAttributesExtractor implements AttributesExtractor<SQSMessage, Void> {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      AttributeKey.stringKey("messaging.message.id");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");
  // copied from MessagingIncubatingAttributes.MessagingSystemIncubatingValues
  private static final String AWS_SQS = "aws_sqs";

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, SQSMessage message) {
    attributes.put(MESSAGING_SYSTEM, AWS_SQS);
    attributes.put(MESSAGING_OPERATION, "process");
    attributes.put(MESSAGING_MESSAGE_ID, message.getMessageId());
    attributes.put(MESSAGING_DESTINATION_NAME, message.getEventSource());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      SQSMessage message,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
