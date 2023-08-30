/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.logging.ApplicationLoggerBridge;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class ApplicationLoggerFactory extends ApplicationLoggerBridge
    implements InternalLogger.Factory {

  private final AtomicBoolean installed = new AtomicBoolean();
  private volatile InternalLogger.Factory actual = null;
  private final ConcurrentMap<String, ApplicationLogger> inMemoryLoggers =
      new ConcurrentHashMap<>();

  private final InMemoryLogStore inMemoryLogStore;

  ApplicationLoggerFactory(InMemoryLogStore inMemoryLogStore) {
    this.inMemoryLogStore = inMemoryLogStore;
  }

  @Override
  protected void install(InternalLogger.Factory applicationLoggerFactory) {
    // just use the first bridge that gets discovered and ignore the rest
    if (!installed.compareAndSet(false, true)) {
      applicationLoggerFactory
          .create(ApplicationLoggerBridge.class.getName())
          .log(
              InternalLogger.Level.WARN,
              "Multiple application logger implementations were provided."
                  + " The javaagent will use the first bridge provided and ignore the following ones (this one).",
              null);
      return;
    }

    // flushing may cause additional classes to be loaded (e.g. slf4j loads logback, which we
    // instrument), so we're doing this repeatedly to clear the in-memory store and preserve the
    // log ordering
    while (inMemoryLogStore.currentSize() > 0) {
      inMemoryLogStore.flush(applicationLoggerFactory);
    }
    inMemoryLogStore.setApplicationLoggerFactory(applicationLoggerFactory);

    // actually install the application logger - from this point, everything will be logged
    // directly through the application logging system
    inMemoryLoggers
        .values()
        .forEach(
            logger -> logger.replaceByActualLogger(applicationLoggerFactory.create(logger.name())));
    this.actual = applicationLoggerFactory;

    // if there are any leftover logs left in the memory store, flush them - this will cause some
    // logs to go out of order, but at least we'll not lose any of them
    if (inMemoryLogStore.currentSize() > 0) {
      inMemoryLogStore.flush(applicationLoggerFactory);
    }

    // finally, free the memory
    inMemoryLogStore.freeMemory();
    inMemoryLoggers.clear();
  }

  @Override
  public InternalLogger create(String name) {
    InternalLogger.Factory bridgedLoggerFactory = this.actual;
    // if the bridge was already installed skip the in-memory logger
    if (bridgedLoggerFactory != null) {
      return bridgedLoggerFactory.create(name);
    }
    return inMemoryLoggers.computeIfAbsent(name, n -> new ApplicationLogger(inMemoryLogStore, n));
  }
}
