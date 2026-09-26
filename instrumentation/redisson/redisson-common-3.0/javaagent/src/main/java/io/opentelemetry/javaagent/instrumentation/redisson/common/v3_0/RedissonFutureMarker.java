/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;

public final class RedissonFutureMarker {
  private final VirtualField<PromiseWrapper<?>, RedissonBatchMarker> markerField;

  public RedissonFutureMarker(VirtualField<PromiseWrapper<?>, RedissonBatchMarker> markerField) {
    this.markerField = markerField;
  }

  public void mark(Object future) {
    if (future instanceof PromiseWrapper) {
      markerField.set((PromiseWrapper<?>) future, new RedissonBatchMarker());
    }
  }

  void unmark(Object future) {
    if (future instanceof PromiseWrapper) {
      markerField.set((PromiseWrapper<?>) future, null);
    }
  }

  boolean isMarked(Object future) {
    return future instanceof PromiseWrapper && markerField.get((PromiseWrapper<?>) future) != null;
  }
}
