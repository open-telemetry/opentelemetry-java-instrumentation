/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DbServerEndpointUtilTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"::", "::1", "2001:db8::1", "::ffff:192.0.2.1", "fe80::1%eth0", "FE80::1"})
  void acceptsIpv6Literals(String host) {
    assertThat(DbServerEndpointUtil.isIpv6Literal(host)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "example.com",
        "1:2",
        "1:2:3:4:5:6:7:8:9",
        "gg::1",
        "::ffff:010.1.1.1",
        "fe80::1%",
        "fe80::1%eth 0"
      })
  void rejectsNonIpv6Literals(String host) {
    assertThat(DbServerEndpointUtil.isIpv6Literal(host)).isFalse();
  }
}
