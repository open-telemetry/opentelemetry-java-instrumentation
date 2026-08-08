/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import javax.annotation.Nullable;

final class RedissonBatchMarker {
  @Nullable private final Object identity;

  RedissonBatchMarker() {
    this(null);
  }

  RedissonBatchMarker(@Nullable Object identity) {
    this.identity = identity;
  }

  boolean matches(Object identity) {
    return this.identity == identity;
  }
}
