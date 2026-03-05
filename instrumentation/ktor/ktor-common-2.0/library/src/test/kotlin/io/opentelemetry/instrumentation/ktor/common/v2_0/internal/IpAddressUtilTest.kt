package io.opentelemetry.instrumentation.ktor.common.v2_0.internal

import org.assertj.core.api.Assertions.assertThat
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
