/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

final class SqsEventTestUtils {

  static SQSMessage newMessage() {
    SQSEvent event = SerializationUtil.fromJson("{\"Records\":[{}]}", SQSEvent.class);
    return event.getRecords().get(0);
  }

  private SqsEventTestUtils() {}
}
