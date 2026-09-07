/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HbaseServerEndpointUtilTest {

  @ParameterizedTest
  @ValueSource(strings = {"::", "::1", "2001:db8::1", "fe80::1%eth0", "FE80::1"})
  void acceptsIpv6Literals(String host) {
    assertThat(HbaseServerEndpointUtil.isIpv6Literal(host)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "fe80::1%3Apassword",
        "fe80::1%2Fpassword",
        "fe80::1%3Fpassword",
        "fe80::1%23password",
        "fe80::1%5Bpassword",
        "fe80::1%5Dpassword",
        "fe80::1%40password",
        "fe80::1%25password"
      })
  void rejectsPercentEncodedUriDelimitersInIpv6Zone(String host) {
    assertThat(HbaseServerEndpointUtil.isIpv6Literal(host)).isFalse();
  }
}
