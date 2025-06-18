/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import java.util.regex.Pattern;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThreadGrouper {
  private static final Pattern SIMILAR_THREAD_NAME_PATTERN = Pattern.compile("\\d+");

  // FIXME only handles substrings of contiguous digits -> a single `x`, but should be good
  // enough for now
  @Nullable
  public String groupedName(RecordedEvent ev) {
    Object thisField = ev.getValue("eventThread");
    if (thisField instanceof RecordedThread) {
      return SIMILAR_THREAD_NAME_PATTERN
          .matcher(((RecordedThread) thisField).getJavaName())
          .replaceAll("x");
    }
    return null;
  }
}
