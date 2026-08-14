/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
abstract class IbmMqRequest {

  static IbmMqRequest create(@Nullable String queueName, @Nullable String queueManagerId) {
    return new AutoValue_IbmMqRequest(queueName, queueManagerId);
  }

  @Nullable
  abstract String getQueueName();

  @Nullable
  abstract String getQueueManagerId();

  String spanName() {
    String queueName = getQueueName();
    if (queueName == null || queueName.isEmpty()) {
      return "MQ Send";
    }
    return queueName + " send";
  }
}
