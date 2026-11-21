/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RocketMqServerAttributesHelperTest {

  @ParameterizedTest
  @MethodSource("targets")
  void formatsConfiguredNameServers(String configured, String address, Long port) {
    assertThat(RocketMqServerAttributesHelper.createAttributes(configured))
        .isEqualTo(
            Attributes.builder().put(SERVER_ADDRESS, address).put(SERVER_PORT, port).build());
  }

  private static Stream<Arguments> targets() {
    return Stream.of(
        argumentSet("single", "ns.example:9876", "ns.example", 9876L),
        argumentSet("IPv4", "127.0.0.1:9876", "127.0.0.1", 9876L),
        argumentSet("bracketed IPv6", "[2001:db8::1]:9876", "2001:db8::1", 9876L),
        argumentSet("unbracketed IPv6", "2001:db8::1:9876", "2001:db8::1", 9876L),
        argumentSet(
            "order and duplicate endpoints",
            "ns-b:9876;ns-a:9877;ns-b:9876",
            "ns-b:9876,ns-a:9877,ns-b:9876",
            null),
        argumentSet(
            "multiple IPv6 endpoints",
            "[2001:db8::1]:9876;2001:db8::2:9877",
            "[2001:db8::1]:9876,[2001:db8::2]:9877",
            null),
        argumentSet(
            "bounded list",
            "ns1:9876;ns2:9876;ns3:9876;ns4:9876;ns5:9876;ns6:9876",
            "ns1:9876,ns2:9876,ns3:9876,ns4:9876,ns5:9876",
            null));
  }

  @ParameterizedTest
  @MethodSource("invalidTargets")
  void omitsIncompleteOrUnsafeTargets(String configured) {
    assertThat(RocketMqServerAttributesHelper.createAttributes(configured))
        .isEqualTo(Attributes.empty());
  }

  private static Stream<Arguments> invalidTargets() {
    return Stream.of(
        argumentSet("unconfigured", (String) null),
        argumentSet("empty", ""),
        argumentSet("missing port", "ns.example"),
        argumentSet("empty port", "ns.example:"),
        argumentSet("nonnumeric port", "ns.example:port"),
        argumentSet("negative port", "ns.example:-1"),
        argumentSet("zero port", "ns.example:0"),
        argumentSet("port out of range", "ns.example:65536"),
        argumentSet("port overflow", "ns.example:2147483648"),
        argumentSet("missing host", ":9876"),
        argumentSet("credentials", "user:password@ns.example:9876"),
        argumentSet("URI", "https://ns.example:9876"),
        argumentSet("incomplete list", "ns1:9876;;ns2:9876"),
        argumentSet("one invalid endpoint", "ns1:9876;invalid:port"),
        argumentSet(
            "invalid endpoint beyond cap",
            "ns1:9876;ns2:9876;ns3:9876;ns4:9876;ns5:9876;user@ns6:9876"));
  }
}
