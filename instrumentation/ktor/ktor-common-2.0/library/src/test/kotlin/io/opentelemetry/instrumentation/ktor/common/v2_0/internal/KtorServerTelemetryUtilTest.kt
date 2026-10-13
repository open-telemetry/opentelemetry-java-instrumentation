/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KtorServerTelemetryUtilTest {

  @Test
  fun `detects versions containing the fix`() {
    assertThat(isKtorContextLeakFixAvailable("3.4.3")).isTrue()
    assertThat(isKtorContextLeakFixAvailable("3.4.4")).isTrue()
    assertThat(isKtorContextLeakFixAvailable("3.5.0")).isTrue()
    assertThat(isKtorContextLeakFixAvailable("4.0.0")).isTrue()
    assertThat(isKtorContextLeakFixAvailable("3.4.3+build.7")).isTrue()
  }

  @Test
  fun `keeps workaround for older or unrecognized versions`() {
    assertThat(isKtorContextLeakFixAvailable("3.4.2")).isFalse()
    assertThat(isKtorContextLeakFixAvailable("3.3.99")).isFalse()
    assertThat(isKtorContextLeakFixAvailable("3.4.3-eap")).isFalse()
    assertThat(isKtorContextLeakFixAvailable("3.4.3-SNAPSHOT")).isFalse()
    assertThat(isKtorContextLeakFixAvailable(null)).isFalse()
    assertThat(isKtorContextLeakFixAvailable("unknown")).isFalse()
    assertThat(isKtorContextLeakFixAvailable("3.4")).isFalse()
  }
}
