/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava;

import com.google.common.util.concurrent.ListenableFuture;
import io.opentelemetry.extension.annotations.WithSpan;

public class TracedWithSpan {
  @WithSpan
  public ListenableFuture<String> listenableFuture(ListenableFuture<String> future) {
    return future;
  }
}
