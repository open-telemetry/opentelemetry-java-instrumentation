/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

final class InMemoryLogStore {

  private final Object lock = new Object();

  @GuardedBy("lock")
  private final ArrayList<InMemoryLog> inMemoryLogs = new ArrayList<>();

  private final int limit;

  InMemoryLogStore(int limit) {
    this.limit = limit;
  }

  void write(InMemoryLog log) {
    synchronized (lock) {
      // just drop the log if hit the limit
      if (limit >= 0 && inMemoryLogs.size() >= limit) {
        return;
      }
      inMemoryLogs.add(log);
    }
  }

  void flush(InternalLogger.Factory applicationLoggerFactory) {
    List<InMemoryLog> copy;
    synchronized (lock) {
      copy = new ArrayList<>(inMemoryLogs);
      inMemoryLogs.clear();
    }

    // calling the application logging system may cause additional classes to be loaded, and this
    // will cause more logs to be added to this store - we need to work on a copy here to avoid a
    // ConcurrentModificationException
    for (InMemoryLog log : copy) {
      applicationLoggerFactory.create(log.name).log(log.level, log.message, log.error);
    }
  }

  int currentSize() {
    synchronized (lock) {
      return inMemoryLogs.size();
    }
  }

  void freeMemory() {
    synchronized (lock) {
      inMemoryLogs.clear();
      inMemoryLogs.trimToSize();
    }
  }

  void dump(PrintStream out) {
    // logs() makes a copy, to avoid ConcurrentModificationException
    for (InMemoryLog log : logs()) {
      log.dump(out);
    }
  }

  // visible for tests
  List<InMemoryLog> logs() {
    synchronized (lock) {
      return new ArrayList<>(inMemoryLogs);
    }
  }
}
