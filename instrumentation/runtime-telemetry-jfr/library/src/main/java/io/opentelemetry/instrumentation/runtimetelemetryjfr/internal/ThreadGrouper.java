/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr.internal;

import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThreadGrouper {

  // FIXME doesn't actually do any grouping, but should be safe for now
  @Nullable
  public String groupedName(RecordedEvent ev) {
    Object thisField = ev.getValue("eventThread");
    if (thisField instanceof RecordedThread) {
      RecordedThread thread = (RecordedThread) thisField;
      return thread.getJavaName();
    }
    return null;
  }
}
