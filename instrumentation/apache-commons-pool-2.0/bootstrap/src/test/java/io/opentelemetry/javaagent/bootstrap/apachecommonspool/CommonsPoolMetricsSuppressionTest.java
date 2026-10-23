/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.apachecommonspool;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

class CommonsPoolMetricsSuppressionTest {

  @Test
  void testSuppression() {
    Context original = Context.current();
    Context suppressed = CommonsPoolMetricsSuppression.suppress(original);

    assertThat(CommonsPoolMetricsSuppression.isSuppressed(original)).isFalse();
    assertThat(CommonsPoolMetricsSuppression.isSuppressed(suppressed)).isTrue();

    try (Scope ignored = suppressed.makeCurrent()) {
      assertThat(CommonsPoolMetricsSuppression.isSuppressed(Context.current())).isTrue();
    }

    assertThat(Context.current()).isSameAs(original);
    assertThat(CommonsPoolMetricsSuppression.isSuppressed(Context.current())).isFalse();
  }
}
