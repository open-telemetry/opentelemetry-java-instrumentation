/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KtorServerTelemetryUtilTest {

  @Test
  fun `resolves version from Ktor JAR name when manifest metadata is missing`() {
    assertThat(resolveKtorVersion(null, "ktor-server-core-jvm-3.0.0.jar")).isEqualTo("3.0.0")
    assertThat(resolveKtorVersion(null, "ktor-server-core-3.4.3.jar")).isEqualTo("3.4.3")
    assertThat(resolveKtorVersion(null, "application-all.jar")).isNull()
    assertThat(resolveKtorVersion("3.6.0", "ktor-server-core-jvm-3.0.0.jar")).isEqualTo("3.6.0")
  }

  @Test
  fun `selects legacy interceptor only for known affected versions`() {
    assertThat(requiresLegacyKtorInterceptor("2.3.12")).isTrue()
    assertThat(requiresLegacyKtorInterceptor("3.0.0")).isTrue()
    assertThat(requiresLegacyKtorInterceptor("3.4.2")).isTrue()
    assertThat(requiresLegacyKtorInterceptor("3.4.3-eap")).isTrue()
    assertThat(requiresLegacyKtorInterceptor("3.4.3-SNAPSHOT")).isTrue()

    assertThat(requiresLegacyKtorInterceptor("3.4.3")).isFalse()
    assertThat(requiresLegacyKtorInterceptor("3.4.4")).isFalse()
    assertThat(requiresLegacyKtorInterceptor("3.5.0")).isFalse()
    assertThat(requiresLegacyKtorInterceptor("4.0.0")).isFalse()
    assertThat(requiresLegacyKtorInterceptor("3.4.3+build.7")).isFalse()
  }

  @Test
  fun `uses dispatcher wrapper when version metadata is unavailable or invalid`() {
    assertThat(requiresLegacyKtorInterceptor(null)).isFalse()
    assertThat(requiresLegacyKtorInterceptor("unknown")).isFalse()
    assertThat(requiresLegacyKtorInterceptor("3.4")).isFalse()
  }
}
