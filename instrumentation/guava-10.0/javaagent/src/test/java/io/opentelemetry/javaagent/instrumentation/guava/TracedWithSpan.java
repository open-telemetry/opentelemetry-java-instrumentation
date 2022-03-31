/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import com.google.common.util.concurrent.ListenableFuture;
import io.opentelemetry.extension.annotations.WithSpan;

final class TracedWithSpan {
  @WithSpan
  ListenableFuture<String> listenableFuture(ListenableFuture<String> future) {
    return future;
  }
}
