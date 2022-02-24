/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;

public final class CompletableFutureWrapper {

  private CompletableFutureWrapper() {}

  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> future, Context context) {
    CompletableFuture<T> result = new CompletableFuture<>();
    future.whenComplete(
        (T value, Throwable throwable) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (throwable != null) {
              result.completeExceptionally(throwable);
            } else {
              result.complete(value);
            }
          }
        });

    return result;
  }
}
