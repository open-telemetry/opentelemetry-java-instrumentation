/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import javax.annotation.Nullable;

abstract class SqsAttributesGetter<REQUEST> implements MessagingAttributesGetter<REQUEST, Void> {

  @Override
  public final String getSystem(REQUEST request) {
    return "aws_sqs";
  }

  @Nullable
  @Override
  public String getDestinationTemplate(REQUEST request) {
    return null;
  }

  @Override
  public final boolean isTemporaryDestination(REQUEST request) {
    return false;
  }

  @Override
  public final boolean isAnonymousDestination(REQUEST request) {
    return false;
  }

  @Nullable
  @Override
  public final String getConversationId(REQUEST request) {
    return null;
  }

  @Nullable
  @Override
  public final Long getMessageBodySize(REQUEST request) {
    return null;
  }

  @Nullable
  @Override
  public final Long getMessageEnvelopeSize(REQUEST request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(REQUEST request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public final String getClientId(REQUEST request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(REQUEST request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  static String queueName(SQSMessage message) {
    String eventSourceArn = message.getEventSourceArn();
    if (eventSourceArn == null) {
      return null;
    }
    int separatorIndex = eventSourceArn.lastIndexOf(':');
    return separatorIndex >= 0 && separatorIndex < eventSourceArn.length() - 1
        ? eventSourceArn.substring(separatorIndex + 1)
        : null;
  }
}
