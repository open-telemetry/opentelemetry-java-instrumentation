/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CassandraServerEndpointUtilTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"::", "::1", "2001:db8::1", "::ffff:192.0.2.1", "fe80::1%eth0", "FE80::1"})
  void acceptsIpv6Literals(String host) {
    assertThat(CassandraServerEndpointUtil.isIpv6Literal(host)).isTrue();
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
        "fe80::1%25password",
        "fe80::1%21password",
        "fe80::1%24password",
        "fe80::1%26password",
        "fe80::1%27password",
        "fe80::1%28password",
        "fe80::1%29password",
        "fe80::1%2Apassword",
        "fe80::1%2Bpassword",
        "fe80::1%2Cpassword",
        "fe80::1%3Bpassword",
        "fe80::1%3Dpassword",
        "fe80::1%5Cpassword"
      })
  void rejectsEncodedUriDelimitersInIpv6ZoneIds(String host) {
    assertThat(CassandraServerEndpointUtil.isIpv6Literal(host)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "fe80::1%3apassword",
        "fe80::1%2fpassword",
        "fe80::1%3fpassword",
        "fe80::1%5bpassword",
        "fe80::1%5dpassword",
        "fe80::1%3dpassword"
      })
  void rejectsLowercaseEncodedUriDelimitersInIpv6ZoneIds(String host) {
    assertThat(CassandraServerEndpointUtil.isIpv6Literal(host)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.0.0.0", "1.2.3.4", "255.255.255.255"})
  void acceptsIpv4Literals(String host) {
    assertThat(CassandraServerEndpointUtil.isIpv4Literal(host)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.2.3", "256.1.1.1", "010.1.1.1", "1.2.3.-4"})
  void rejectsNonIpv4Literals(String host) {
    assertThat(CassandraServerEndpointUtil.isIpv4Literal(host)).isFalse();
  }
}
