/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;
import javax.annotation.Nullable;

class JmsMessageTimeExtractor implements TimeExtractor<MessageWithDestination, Void> {

  @Override
  public Instant extractStartTime(MessageWithDestination request) {
    return request.startTime();
  }

  @Override
  public Instant extractEndTime(
      MessageWithDestination request, @Nullable Void unused, @Nullable Throwable error) {
    return request.endTime();
  }
}
