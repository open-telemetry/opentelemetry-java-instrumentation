/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.api.trace.SpanKind;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
public class TracedWithSpan {

  public TracedWithSpan() {}

  // used to verify that constructor with @WithSpan annotation doesn't break instrumentation
  @io.opentelemetry.extension.annotations.WithSpan
  public TracedWithSpan(String unused) {}

  @io.opentelemetry.extension.annotations.WithSpan
  public String otel() {
    return "hello!";
  }

  @io.opentelemetry.extension.annotations.WithSpan("manualName")
  public String namedOtel() {
    return "hello!";
  }

  @io.opentelemetry.extension.annotations.WithSpan
  public String ignored() {
    return "hello!";
  }

  @io.opentelemetry.extension.annotations.WithSpan(kind = SpanKind.PRODUCER)
  public String someKind() {
    return "hello!";
  }

  @io.opentelemetry.extension.annotations.WithSpan(kind = SpanKind.SERVER)
  public String server() {
    return otel();
  }

  @io.opentelemetry.extension.annotations.WithSpan
  public String withSpanAttributes(
      @io.opentelemetry.extension.annotations.SpanAttribute String implicitName,
      @io.opentelemetry.extension.annotations.SpanAttribute("explicitName") String parameter,
      @io.opentelemetry.extension.annotations.SpanAttribute("nullAttribute") String nullAttribute,
      String notTraced) {

    return "hello!";
  }

  @io.opentelemetry.extension.annotations.WithSpan
  public CompletionStage<String> completionStage(CompletableFuture<String> future) {
    return future;
  }

  @io.opentelemetry.extension.annotations.WithSpan
  public CompletableFuture<String> completableFuture(CompletableFuture<String> future) {
    return future;
  }
}
