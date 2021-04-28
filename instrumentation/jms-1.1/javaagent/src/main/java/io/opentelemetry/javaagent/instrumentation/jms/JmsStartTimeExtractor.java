/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.instrumentation.api.instrumenter.StartTimeExtractor;
import java.time.Instant;

public final class JmsStartTimeExtractor implements StartTimeExtractor<MessageWithDestination> {
  private final StartTimeExtractor<MessageWithDestination> fallback =
      StartTimeExtractor.getDefault();

  @Override
  public Instant extract(MessageWithDestination message) {
    Instant startTime = message.getStartTime();
    return startTime != null ? startTime : fallback.extract(message);
  }
}
