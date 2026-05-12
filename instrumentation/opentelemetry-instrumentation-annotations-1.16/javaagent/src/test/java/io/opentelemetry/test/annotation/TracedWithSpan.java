/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class TracedWithSpan {

  TracedWithSpan() {}

  // used to verify that constructor with @WithSpan annotation doesn't break instrumentation
  @WithSpan
  TracedWithSpan(String unused) {}

  @WithSpan
  String otel() {
    return "hello!";
  }

  @WithSpan("manualName")
  String namedOtel() {
    return "hello!";
  }

  @WithSpan
  String ignored() {
    return "hello!";
  }

  @WithSpan(kind = SpanKind.PRODUCER)
  String someKind() {
    return "hello!";
  }

  @WithSpan(kind = SpanKind.SERVER)
  String server() {
    return otel();
  }

  @WithSpan
  String withSpanAttributes(
      @SpanAttribute String implicitName,
      @SpanAttribute("explicitName") String parameter,
      @SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {

    return "hello!";
  }

  @WithSpan
  CompletionStage<String> completionStage(CompletableFuture<String> future) {
    return future;
  }

  @WithSpan
  CompletableFuture<String> completableFuture(CompletableFuture<String> future) {
    return future;
  }

  @WithSpan(inheritContext = false)
  String withoutParent() {
    return "hello!";
  }

  @WithSpan(kind = SpanKind.CONSUMER)
  String consumer() {
    return withoutParent();
  }
}
