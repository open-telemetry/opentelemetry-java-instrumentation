/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.TimeExtractor;
import java.time.Instant;

class JmsMessageTimeExtractor implements TimeExtractor<MessageWithDestination, Void> {

  @Override
  public Instant extractStartTime(Context parentContext, MessageWithDestination request) {
    return request.startTime();
  }

  @Override
  public Instant extractEndTime(Context context, MessageWithDestination request) {
    return request.endTime();
  }
}
