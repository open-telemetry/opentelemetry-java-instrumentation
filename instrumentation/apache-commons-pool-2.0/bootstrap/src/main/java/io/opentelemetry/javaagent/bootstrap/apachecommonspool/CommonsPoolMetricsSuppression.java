/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.apachecommonspool;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

public final class CommonsPoolMetricsSuppression {
  private static final ContextKey<Boolean> KEY =
      ContextKey.named("opentelemetry-commons-pool-metrics-suppressed");

  public static Context suppress(Context context) {
    return context.with(KEY, true);
  }

  public static boolean isSuppressed(Context context) {
    return Boolean.TRUE.equals(context.get(KEY));
  }

  private CommonsPoolMetricsSuppression() {}
}
