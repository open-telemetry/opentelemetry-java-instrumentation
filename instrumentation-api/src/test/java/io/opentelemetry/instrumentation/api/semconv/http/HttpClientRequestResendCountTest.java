/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

class HttpClientRequestResendCountTest {

  @Test
  void resendCountShouldBeZeroWhenNotInitialized() {
    assertThat(HttpClientRequestResendCount.getAndIncrement(Context.root())).isEqualTo(0);
    assertThat(HttpClientRequestResendCount.getAndIncrement(Context.root())).isEqualTo(0);
  }

  @Test
  void incrementResendCount() {
    Context context = HttpClientRequestResendCount.initialize(Context.root());

    assertThat(HttpClientRequestResendCount.getAndIncrement(context)).isEqualTo(0);
    assertThat(HttpClientRequestResendCount.getAndIncrement(context)).isEqualTo(1);
  }
}
