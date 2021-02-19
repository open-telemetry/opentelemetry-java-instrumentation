/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.utils;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupportabilityMetrics {
  private static final Logger log = LoggerFactory.getLogger(SupportabilityMetrics.class);
  private final boolean agentDebugEnabled;
  private final Consumer<String> reporter;

  private final ConcurrentMap<String, EnumMap<SpanKind, AtomicInteger>> suppressionCounters =
      new ConcurrentHashMap<>();

  public SupportabilityMetrics(Config config) {
    this(config, log::debug);
  }

  //visible for testing
  SupportabilityMetrics(Config config, Consumer<String> reporter) {
    agentDebugEnabled = config.isAgentDebugEnabled();
    this.reporter = reporter;
  }

  public void recordSuppressedSpan(SpanKind kind, String instrumentationName) {
    if (!agentDebugEnabled) {
      return;
    }
    // note: there's definitely a race here, but since this is just debug information, I think
    // we can live with the possibility that we might lose a count or two.
    EnumMap<SpanKind, AtomicInteger> countersByKind =
        suppressionCounters.computeIfAbsent(
            instrumentationName, s -> new EnumMap<>(SpanKind.class));

    countersByKind.computeIfAbsent(kind, k -> new AtomicInteger()).incrementAndGet();
  }

  //visible for testing
  void report() {
    suppressionCounters.forEach(
        (instrumentationName, countsByKind) -> {
          countsByKind.forEach(
              (spanKind, counter) -> {
                reporter.accept(
                    "Suppressed Spans by '"
                        + instrumentationName
                        + "' ("
                        + spanKind
                        + ") : "
                        + counter.getAndUpdate(operand -> 0));
              });
        });
  }

  public SupportabilityMetrics start() {
    if (agentDebugEnabled) {
      Executors.newScheduledThreadPool(1)
          .scheduleAtFixedRate(this::report,5, 5, TimeUnit.SECONDS);
    }
    return this;
  }
}
