/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ReactorHookRegistrationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @BeforeEach
  void setUp() {
    ContextPropagationOperator.builder().build().resetOnEachOperator();
  }

  @AfterEach
  void tearDown() {
    ContextPropagationOperator.builder().build().resetOnEachOperator();
  }

  @Test
  void addFilterRegistersReactorHookSoContextPropagatesAcrossThreads() throws Exception {
    SpringWebfluxClientTelemetry telemetry =
        SpringWebfluxClientTelemetry.builder(testing.getOpenTelemetry()).build();
    List<ExchangeFilterFunction> filters = new ArrayList<>();
    telemetry.addFilter(filters); // must register hook internally

    Tracer tracer = testing.getOpenTelemetry().getTracer("test");
    AtomicReference<Span> capturedSpan = new AtomicReference<>();

    Span parent = tracer.spanBuilder("parent").startSpan();
    try (Scope ignored = parent.makeCurrent()) {
      Mono.fromCallable(Span::current)
          .subscribeOn(Schedulers.boundedElastic()) // thread switch
          .doOnNext(capturedSpan::set)
          .block();
    } finally {
      parent.end();
    }

    // Context must survive the thread switch — span IDs must match
    assertThat(capturedSpan.get().getSpanContext().getSpanId())
        .as("OTel context should propagate across thread boundary after addFilter()")
        .isEqualTo(parent.getSpanContext().getSpanId());
  }

  @Test
  void createWebFilterRegistersReactorHookSoContextPropagatesAcrossThreads() throws Exception {
    SpringWebfluxServerTelemetry telemetry =
        SpringWebfluxServerTelemetry.builder(testing.getOpenTelemetry()).build();
    telemetry.createWebFilter(); // must register hook internally

    Tracer tracer = testing.getOpenTelemetry().getTracer("test");
    AtomicReference<Span> capturedSpan = new AtomicReference<>();

    Span parent = tracer.spanBuilder("parent").startSpan();
    try (Scope ignored = parent.makeCurrent()) {
      Mono.fromCallable(Span::current)
          .subscribeOn(Schedulers.boundedElastic()) // thread switch
          .doOnNext(capturedSpan::set)
          .block();
    } finally {
      parent.end();
    }

    assertThat(capturedSpan.get().getSpanContext().getSpanId())
        .as("OTel context should propagate across thread boundary after createWebFilter()")
        .isEqualTo(parent.getSpanContext().getSpanId());
  }
}
