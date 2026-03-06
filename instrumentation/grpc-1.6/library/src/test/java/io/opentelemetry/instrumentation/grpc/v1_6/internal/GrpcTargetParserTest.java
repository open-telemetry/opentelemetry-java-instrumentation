/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class GrpcTargetParserTest {

  @ParameterizedTest
  @MethodSource("targetProvider")
  void parse(String target, String expectedAddress, Integer expectedPort) {
    ParsedTarget result = GrpcTargetParser.parse(target);

    assertThat(result).isNotNull();
    assertThat(result.getAddress()).isEqualTo(expectedAddress);
    assertThat(result.getPort()).isEqualTo(expectedPort);
  }

  static Stream<Arguments> targetProvider() {
    return Stream.of(
        // dns:/// scheme (triple slash)
        Arguments.of("dns:///myhost", "myhost", null),
        Arguments.of("dns:///myhost:8080", "myhost", 8080),

        // dns: scheme (single colon)
        Arguments.of("dns:myhost", "myhost", null),
        Arguments.of("dns:myhost:8080", "myhost", 8080),

        // bare host:port (no scheme)
        Arguments.of("myhost", "myhost", null),
        Arguments.of("myhost:8080", "myhost", 8080),
        Arguments.of("localhost:443", "localhost", 443),

        // unix schemes
        Arguments.of("unix:///var/run/grpc.sock", "/var/run/grpc.sock", null),
        Arguments.of("unix:/var/run/grpc.sock", "/var/run/grpc.sock", null),
        Arguments.of("unix-abstract:name", "name", null),

        // ipv4 scheme
        Arguments.of("ipv4:192.168.0.1:8080", "ipv4:192.168.0.1:8080", null),

        // ipv6 scheme
        Arguments.of("ipv6:[::1]:8080", "ipv6:[::1]:8080", null),

        // IPv6 in brackets (bare)
        Arguments.of("[::1]:8080", "::1", 8080),
        Arguments.of("[::1]", "::1", null),

        // bare IPv6 (no brackets) — treated as host with no port
        Arguments.of("::1", "::1", null),

        // unknown scheme with ://
        Arguments.of("xds:///myservice", "xds:///myservice", null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void parseNullOrEmpty(String target) {
    assertThat(GrpcTargetParser.parse(target)).isNull();
  }
}
