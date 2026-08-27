/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
        Arguments.of("DNS:///myhost:443", "myhost", 443),

        // dns: scheme (single colon)
        Arguments.of("dns:myhost", "myhost", null),
        Arguments.of("dns:myhost:8080", "myhost", 8080),
        Arguments.of("DNS:myhost:443", "myhost", 443),
        Arguments.of("dns:/myhost", "myhost", null),
        Arguments.of("dns:/myhost:8080", "myhost", 8080),
        Arguments.of("dns:///%5B2001:db8::1%5D:443", "2001:db8::1", 443),

        // bare host:port (no scheme)
        Arguments.of("myhost", "myhost", null),
        Arguments.of("myhost:8080", "myhost", 8080),
        Arguments.of("localhost:443", "localhost", 443),

        // unix schemes
        Arguments.of("unix:///var/run/grpc.sock", "/var/run/grpc.sock", null),
        Arguments.of("UNIX:///var/run/grpc.sock", "/var/run/grpc.sock", null),
        Arguments.of("unix:/var/run/grpc.sock", "/var/run/grpc.sock", null),
        Arguments.of("unix-abstract:name", "name", null),
        Arguments.of("UNIX-ABSTRACT:name", "name", null),
        Arguments.of("unix:///tmp/a%20b.sock", "/tmp/a b.sock", null),
        Arguments.of("unix:///tmp/a.sock?x", "/tmp/a.sock", null),
        Arguments.of("unix:///tmp/a%.sock", "/tmp/a%.sock", null),

        // ipv4 scheme
        Arguments.of("ipv4:192.168.0.1:8080", "ipv4:192.168.0.1:8080", null),
        Arguments.of("IPV4:192.168.0.1:8080", "IPV4:192.168.0.1:8080", null),

        // ipv6 scheme
        Arguments.of("ipv6:[::1]:8080", "ipv6:[::1]:8080", null),

        // IPv6 in brackets (bare)
        Arguments.of("[::1]:8080", "::1", 8080),
        Arguments.of("[::1]", "::1", null),

        // bare IPv6 (no brackets) — treated as host with no port
        Arguments.of("::1", "::1", null),

        // xds scheme is preserved
        Arguments.of("xds:///myservice", "xds:///myservice", null),
        Arguments.of("XDS:///myservice", "XDS:///myservice", null),
        Arguments.of("xds:/myservice", "xds:/myservice", null),

        // unknown schemes are preserved
        Arguments.of("consul:orders", "consul:orders", null),
        Arguments.of("myhost:", "myhost:", null),
        Arguments.of("myhost:abc", "myhost:abc", null),

        // known schemes with missing/invalid ports preserve the parsed host
        Arguments.of("dns:myhost:abc", "myhost", null),
        Arguments.of("dns:///myhost:", "myhost", null));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void parseNullOrEmpty(String target) {
    assertThat(GrpcTargetParser.parse(target)).isNull();
  }

  @Test
  void parseSyntheticDirectAddressReturnsNull() {
    assertThat(GrpcTargetParser.parse("directaddress:///localhost/127.0.0.1:443")).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "dns:",
        "dns://",
        "dns:///",
        "unix:",
        "unix://",
        "unix-abstract:",
        "unix-abstract://",
        ":8080",
        ":",
        "[]:8080",
        "[]"
      })
  void parseEmptyEndpointReturnsNull(String target) {
    // "dns:" -> empty after single-colon scheme
    // "dns:///" -> empty endpoint after authority slash
    // "unix:" / "unix-abstract:" -> empty after single-colon scheme
    // "unix://" / "unix-abstract://" -> empty endpoint after scheme
    // ":8080", ":" -> empty host before port
    // "[]:8080", "[]" -> empty host inside IPv6 brackets
    assertThat(GrpcTargetParser.parse(target)).isNull();
  }
}
