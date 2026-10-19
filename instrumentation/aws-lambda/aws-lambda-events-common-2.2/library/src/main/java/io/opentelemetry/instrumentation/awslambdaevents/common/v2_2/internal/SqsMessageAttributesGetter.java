/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import javax.annotation.Nullable;

final class SqsMessageAttributesGetter extends SqsAttributesGetter<SQSMessage, Boolean> {

  @Nullable
  @Override
  public String getDestination(SQSMessage message) {
    return queueName(message);
  }

  @Nullable
  @Override
  public String getMessageId(SQSMessage message, @Nullable Boolean unused) {
    return message.getMessageId();
  }
}
