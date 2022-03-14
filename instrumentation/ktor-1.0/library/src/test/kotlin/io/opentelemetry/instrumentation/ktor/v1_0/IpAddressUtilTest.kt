/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.Test

class IpAddressUtilTest {

  @Test
  fun `test ip address`() {
    assertThat(isIpAddress("2001:0660:7401:0200:0000:0000:0edf:bdd7")).isTrue()
    assertThat(isIpAddress("2001:0660:7401:0200:0000:0000:0edf:bdd7:33")).isFalse()
    assertThat(isIpAddress("127.0.0.1")).isTrue()
    assertThat(isIpAddress("127.0.0.1.1")).isFalse()
    assertThat(isIpAddress("localhost")).isFalse()
  }
}
