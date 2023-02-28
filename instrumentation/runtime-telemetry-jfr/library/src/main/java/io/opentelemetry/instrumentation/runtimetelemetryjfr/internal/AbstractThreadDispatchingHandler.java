/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;


/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public abstract class AbstractThreadDispatchingHandler implements RecordedEventHandler {
  // Will need pruning code for fast-cycling thread frameworks to prevent memory leaks
  private final Map<String, Consumer<RecordedEvent>> perThread = new HashMap<>();
  private final ThreadGrouper grouper;

  protected AbstractThreadDispatchingHandler(ThreadGrouper grouper) {
    this.grouper = grouper;
  }

  @Override
  public abstract String getEventName();

  public abstract Consumer<RecordedEvent> createPerThreadSummarizer(String threadName);

  @Override
  public void accept(RecordedEvent ev) {
    grouper
        .groupedName(ev)
        .ifPresent(
            groupedThreadName ->
                perThread
                    .computeIfAbsent(groupedThreadName, this::createPerThreadSummarizer)
                    .accept(ev));
  }
}
