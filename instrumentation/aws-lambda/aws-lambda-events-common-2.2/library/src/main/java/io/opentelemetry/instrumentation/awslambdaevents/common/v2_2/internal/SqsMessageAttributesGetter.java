/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import javax.annotation.Nullable;

final class SqsMessageAttributesGetter extends SqsAttributesGetter<SQSMessage> {

  @Nullable
  @Override
  public String getDestination(SQSMessage message) {
    return emitStableMessagingSemconv() ? queueName(message) : message.getEventSource();
  }

  @Nullable
  @Override
  public String getMessageId(SQSMessage message, @Nullable Void unused) {
    return message.getMessageId();
  }
}
