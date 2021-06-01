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
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SupportabilityMetrics {
  private static final Logger log = LoggerFactory.getLogger(SupportabilityMetrics.class);
  private final boolean agentDebugEnabled;
  private final Consumer<String> reporter;

  private final ConcurrentMap<String, KindCounters> suppressionCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, LongAdder> counters = new ConcurrentHashMap<>();

  private static final SupportabilityMetrics INSTANCE =
      new SupportabilityMetrics(Config.get(), log::debug).start();

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

    counters.computeIfAbsent(counterName, k -> new LongAdder()).increment();
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
          long value = counter.sumThenReset();
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
    private final LongAdder server = new LongAdder();
    private final LongAdder client = new LongAdder();
    private final LongAdder internal = new LongAdder();
    private final LongAdder consumer = new LongAdder();
    private final LongAdder producer = new LongAdder();

    void increment(SpanKind kind) {
      switch (kind) {
        case INTERNAL:
          internal.increment();
          break;
        case SERVER:
          server.increment();
          break;
        case CLIENT:
          client.increment();
          break;
        case PRODUCER:
          producer.increment();
          break;
        case CONSUMER:
          consumer.increment();
          break;
      }
    }

    long getAndReset(SpanKind kind) {
      switch (kind) {
        case INTERNAL:
          return internal.sumThenReset();
        case SERVER:
          return server.sumThenReset();
        case CLIENT:
          return client.sumThenReset();
        case PRODUCER:
          return producer.sumThenReset();
        case CONSUMER:
          return consumer.sumThenReset();
      }
      return 0;
    }
  }
}
