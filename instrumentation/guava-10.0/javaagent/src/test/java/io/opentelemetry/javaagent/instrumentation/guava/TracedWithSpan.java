/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.opentelemetry.extension.annotations.WithSpan;

final class TracedWithSpan {
  static final IllegalArgumentException FAILURE = new IllegalArgumentException("Boom");

  @WithSpan
  SettableFuture<String> completable() {
    return SettableFuture.create();
  }

  @WithSpan
  ListenableFuture<String> alreadySucceeded() {
    return Futures.immediateFuture("Value");
  }

  @WithSpan
  ListenableFuture<String> alreadyFailed() {
    return Futures.immediateFailedFuture(FAILURE);
  }
}
