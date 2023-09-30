/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class HttpClientResendCountTest {

  @Test
  void resendCountShouldBeZeroWhenNotInitialized() {
    assertThat(HttpClientResendCount.getAndIncrement(Context.root())).isEqualTo(0);
    assertThat(HttpClientResendCount.getAndIncrement(Context.root())).isEqualTo(0);
  }

  @Test
  void incrementResendCount() {
    Context context = HttpClientResendCount.initialize(Context.root());

    assertThat(HttpClientResendCount.getAndIncrement(context)).isEqualTo(0);
    assertThat(HttpClientResendCount.getAndIncrement(context)).isEqualTo(1);
  }
}
