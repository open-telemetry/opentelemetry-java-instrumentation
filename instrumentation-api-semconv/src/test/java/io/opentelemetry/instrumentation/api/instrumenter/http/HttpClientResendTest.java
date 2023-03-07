/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class HttpClientResendTest {

  @Test
  void resendCountShouldBeZeroWhenNotInitialized() {
    assertThat(HttpClientResend.getAndIncrement(Context.root())).isEqualTo(0);
    assertThat(HttpClientResend.getAndIncrement(Context.root())).isEqualTo(0);
  }

  @Test
  void incrementResendCount() {
    Context context = HttpClientResend.initialize(Context.root());

    assertThat(HttpClientResend.getAndIncrement(context)).isEqualTo(0);
    assertThat(HttpClientResend.getAndIncrement(context)).isEqualTo(1);
  }
}
