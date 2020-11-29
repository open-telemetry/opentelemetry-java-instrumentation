/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.apache.log4j.AppenderSkeleton
import org.apache.log4j.spi.LoggingEvent

class ListAppender extends AppenderSkeleton {
  static events = new ArrayList<LoggingEvent>()

  @Override
  protected void append(LoggingEvent loggingEvent) {
    events.add(loggingEvent)
  }

  @Override
  boolean requiresLayout() {
    return false
  }

  @Override
  void close() {
  }

  static clearEvents() {
    events.clear()
  }
}
