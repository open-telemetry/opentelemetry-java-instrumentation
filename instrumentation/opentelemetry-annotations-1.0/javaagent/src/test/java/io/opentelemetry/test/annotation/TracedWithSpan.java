/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test.annotation;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.extension.annotations.WithSpan;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class TracedWithSpan {

  @WithSpan
  public String otel() {
    return "hello!";
  }

  @WithSpan("manualName")
  public String namedOtel() {
    return "hello!";
  }

  @WithSpan
  public String ignored() {
    return "hello!";
  }

  @WithSpan(kind = SpanKind.PRODUCER)
  public String oneOfAKind() {
    return "hello!";
  }

  @WithSpan(kind = SpanKind.SERVER)
  public String server() {
    return otel();
  }

  @WithSpan(kind = SpanKind.SERVER)
  public String nestedServers() {
    return innerServer();
  }

  @WithSpan(kind = SpanKind.SERVER)
  public String innerServer() {
    return "hello!";
  }

  @WithSpan(kind = SpanKind.CLIENT)
  public String nestedClients() {
    return innerClient();
  }

  @WithSpan(kind = SpanKind.CLIENT)
  public String innerClient() {
    return "hello!";
  }

  @WithSpan
  public CompletionStage<String> completionStage(CompletableFuture<String> future) {
    return future;
  }

  @WithSpan
  public CompletableFuture<String> completableFuture(CompletableFuture<String> future) {
    return future;
  }
}
