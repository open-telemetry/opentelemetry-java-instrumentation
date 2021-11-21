/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SupportabilityMetrics {
  private static final Logger logger = LoggerFactory.getLogger(SupportabilityMetrics.class);
  private final boolean agentDebugEnabled;
  private final Consumer<String> reporter;

  private final ConcurrentMap<String, KindCounters> suppressionCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

  private static final SupportabilityMetrics INSTANCE =
      new SupportabilityMetrics(Config.get(), logger::debug).start();

  public static SupportabilityMetrics instance() {
    return INSTANCE;
  }

  // visible for testing
  SupportabilityMetrics(Config config, Consumer<String> reporter) {
    agentDebugEnabled = config.isAgentDebugEnabled();
    this.reporter = reporter;
  }

  public void recordSuppressedSpan(SpanKind kind, String instrumentationName) {
    if (!agentDebugEnabled) {
      return;
    }

    suppressionCounters
        .computeIfAbsent(instrumentationName, s -> new KindCounters())
        .increment(kind);
  }

  public void incrementCounter(String counterName) {
    if (!agentDebugEnabled) {
      return;
    }

    counters.computeIfAbsent(counterName, k -> new AtomicLong()).incrementAndGet();
  }

  // visible for testing
  void report() {
    suppressionCounters.forEach(
        (instrumentationName, countsByKind) -> {
          for (SpanKind kind : SpanKind.values()) {
            long value = countsByKind.getAndReset(kind);
            if (value > 0) {
              reporter.accept(
                  "Suppressed Spans by '" + instrumentationName + "' (" + kind + ") : " + value);
            }
          }
        });
    counters.forEach(
        (counterName, counter) -> {
          long value = counter.getAndSet(0);
          if (value > 0) {
            reporter.accept("Counter '" + counterName + "' : " + value);
          }
        });
  }

  SupportabilityMetrics start() {
    if (agentDebugEnabled) {
      Executors.newScheduledThreadPool(
              1,
              runnable -> {
                Thread result = new Thread(runnable, "supportability_metrics_reporter");
                result.setDaemon(true);
                result.setContextClassLoader(null);
                return result;
              })
          .scheduleAtFixedRate(this::report, 5, 5, TimeUnit.SECONDS);
    }
    return this;
  }

  public static final class CounterNames {
    public static final String SQL_STATEMENT_SANITIZER_CACHE_MISS =
        "SqlStatementSanitizer cache miss";

    private CounterNames() {}
  }

  // this class is threadsafe.
  private static class KindCounters {
    private final AtomicLong server = new AtomicLong();
    private final AtomicLong client = new AtomicLong();
    private final AtomicLong internal = new AtomicLong();
    private final AtomicLong consumer = new AtomicLong();
    private final AtomicLong producer = new AtomicLong();

    void increment(SpanKind kind) {
      switch (kind) {
        case INTERNAL:
          internal.incrementAndGet();
          break;
        case SERVER:
          server.incrementAndGet();
          break;
        case CLIENT:
          client.incrementAndGet();
          break;
        case PRODUCER:
          producer.incrementAndGet();
          break;
        case CONSUMER:
          consumer.incrementAndGet();
          break;
      }
    }

    long getAndReset(SpanKind kind) {
      switch (kind) {
        case INTERNAL:
          return internal.getAndSet(0);
        case SERVER:
          return server.getAndSet(0);
        case CLIENT:
          return client.getAndSet(0);
        case PRODUCER:
          return producer.getAndSet(0);
        case CONSUMER:
          return consumer.getAndSet(0);
      }
      return 0;
    }
  }
}
