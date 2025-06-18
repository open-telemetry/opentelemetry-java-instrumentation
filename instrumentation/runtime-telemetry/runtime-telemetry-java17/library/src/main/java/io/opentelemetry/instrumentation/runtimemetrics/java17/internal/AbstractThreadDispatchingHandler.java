/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public abstract class AbstractThreadDispatchingHandler implements RecordedEventHandler {
  // Use an access-ordered LinkedHashMap so we get a bounded LRU cache
  private final Map<String, Consumer<RecordedEvent>> perThread =
      new LinkedHashMap<String, Consumer<RecordedEvent>>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Consumer<RecordedEvent>> eldest) {
          // Bound this map to prevent memory leaks with fast-cycling thread frameworks
          return size() > 512;
        }
      };
  private final ThreadGrouper grouper;

  protected AbstractThreadDispatchingHandler(ThreadGrouper grouper) {
    this.grouper = grouper;
  }

  @Override
  public abstract String getEventName();

  public abstract Consumer<RecordedEvent> createPerThreadSummarizer(String threadName);

  @Override
  public void accept(RecordedEvent ev) {
    String groupedName = grouper.groupedName(ev);
    if (groupedName != null) {
      perThread.computeIfAbsent(groupedName, this::createPerThreadSummarizer).accept(ev);
    }
  }
}
