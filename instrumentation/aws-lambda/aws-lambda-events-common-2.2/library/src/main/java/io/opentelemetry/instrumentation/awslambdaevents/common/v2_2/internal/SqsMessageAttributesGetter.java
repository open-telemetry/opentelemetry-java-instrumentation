/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import javax.annotation.Nullable;

final class SqsMessageAttributesGetter extends SqsAttributesGetter<SQSMessage> {

  @Nullable
  @Override
  public String getDestination(SQSMessage message) {
    return message.getEventSource();
  }

  @Nullable
  @Override
  public String getMessageId(SQSMessage message, @Nullable Void unused) {
    return message.getMessageId();
  }
}
