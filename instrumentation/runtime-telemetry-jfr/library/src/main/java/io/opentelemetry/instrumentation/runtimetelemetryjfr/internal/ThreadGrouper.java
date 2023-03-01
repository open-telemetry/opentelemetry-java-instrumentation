/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr.internal;

import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThreadGrouper {
  // FIXME doesn't actually do any grouping, but should be safe for now
  public Optional<String> groupedName(RecordedEvent ev) {
    Object thisField = ev.getValue("eventThread");
    if (thisField instanceof RecordedThread) {
      RecordedThread thread = (RecordedThread) thisField;
      return Optional.of(thread.getJavaName());
    }
    return Optional.empty();
  }
}
