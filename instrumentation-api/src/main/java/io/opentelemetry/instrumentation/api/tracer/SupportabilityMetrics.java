/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

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

class SupportabilityMetrics {
  private static final Logger log = LoggerFactory.getLogger(SupportabilityMetrics.class);
  private final boolean agentDebugEnabled;
  private final Consumer<String> reporter;

  private final ConcurrentMap<String, KindCounters> suppressionCounters = new ConcurrentHashMap<>();

  public SupportabilityMetrics(Config config) {
    this(config, log::debug);
  }

  // visible for testing
  SupportabilityMetrics(Config config, Consumer<String> reporter) {
    agentDebugEnabled = config.isAgentDebugEnabled();
    this.reporter = reporter;
  }

  void recordSuppressedSpan(SpanKind kind, String instrumentationName) {
    if (!agentDebugEnabled) {
      return;
    }

    suppressionCounters
        .computeIfAbsent(instrumentationName, s -> new KindCounters())
        .increment(kind);
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
  }

  SupportabilityMetrics start() {
    if (agentDebugEnabled) {
      Executors.newScheduledThreadPool(
              1,
              runnable -> {
                Thread result = new Thread(runnable, "supportability_metrics_reporter");
                result.setDaemon(true);
                return result;
              })
          .scheduleAtFixedRate(this::report, 5, 5, TimeUnit.SECONDS);
    }
    return this;
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
        default:
          // in case a new kind gets added, we don't want to fail.
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
        default:
          // in case a new kind gets added, we don't want to fail.
          return 0;
      }
    }
  }
}
