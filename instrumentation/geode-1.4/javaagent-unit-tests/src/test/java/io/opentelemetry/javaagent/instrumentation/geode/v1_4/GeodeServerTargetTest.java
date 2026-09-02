/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GeodeServerTargetTest {

  @Test
  void singleServerOnTheDefaultPortOmitsThePort() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("cache.example", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleServerOnANonDefaultPortUsesServerPort() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("cache.example", 40405);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isEqualTo(40405);
  }

  @Test
  void serversOnTheDefaultPortOmitPorts() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("two.example", 40404);
    builder.addServer("2001:db8::1", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("2001:db8::1,two.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void serversOnACommonNonDefaultPortEmbedEveryPortAndKeepDuplicates() {
    GeodeServerTarget.Builder first = GeodeServerTarget.builder();
    first.addServer("two.example", 40405);
    first.addServer("one.example", 40405);
    first.addServer("two.example", 40405);

    GeodeServerTarget.Builder second = GeodeServerTarget.builder();
    second.addServer("two.example", 40405);
    second.addServer("two.example", 40405);
    second.addServer("one.example", 40405);

    assertThat(first.build().getAddress())
        .isEqualTo("one.example:40405,two.example:40405,two.example:40405")
        .isEqualTo(second.build().getAddress());
    assertThat(first.build().getPort()).isNull();
    assertThat(second.build().getPort()).isNull();
  }

  @Test
  void serversOnDifferentPortsEmbedEveryPort() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("two.example", 40405);
    builder.addServer("2001:db8::1", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("[2001:db8::1]:40404,two.example:40405");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void fiveServersAreAllReported() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("e.example", 40404);
    builder.addServer("d.example", 40404);
    builder.addServer("a.example", 40404);
    builder.addServer("c.example", 40404);
    builder.addServer("b.example", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("a.example,b.example,c.example,d.example,e.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sixServersAreSortedBeforeTheFirstFiveAreReported() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("z.example", 40404);
    builder.addServer("e.example", 40404);
    builder.addServer("d.example", 40404);
    builder.addServer("c.example", 40404);
    builder.addServer("b.example", 40404);
    builder.addServer("a.example", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("a.example,b.example,c.example,d.example,e.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void nonDefaultPortAfterTheFirstFiveChangesTheCompleteListPortMode() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("a.example", 40404);
    builder.addServer("b.example", 40404);
    builder.addServer("c.example", 40404);
    builder.addServer("d.example", 40404);
    builder.addServer("e.example", 40404);
    builder.addServer("z.example", 40405);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress())
        .isEqualTo(
            "a.example:40404,b.example:40404,c.example:40404," + "d.example:40404,e.example:40404");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void explicitServerIsPreferredOverTheServerGroup() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("cache.example", 40404);
    builder.setServerGroup("orders");

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void serverGroupWithoutLocatorsHasNoTarget() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.setServerGroup("  orders  ");

    assertThat(builder.build()).isNull();
  }

  @Test
  void blankServerGroupNamesNothing() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.setServerGroup("   ");
    builder.addServer("cache.example", 40404);

    assertThat(builder.build().getAddress()).isEqualTo("cache.example");
  }

  @Test
  void poolWithoutServersOrAGroupHasNoTarget() {
    assertThat(GeodeServerTarget.builder().build()).isNull();
  }

  @Test
  void locatorsWithoutAGroupAreReportedAsAPlainEndpointList() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("two.example", 10335);
    builder.addLocator("2001:db8::1", 10334);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("[2001:db8::1]:10334,two.example:10335");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void locatorPermutationsAreSortedAndKeepDuplicatesWithSharedGroupSuffix() {
    GeodeServerTarget.Builder first = GeodeServerTarget.builder();
    first.addLocator("two.example", 10335);
    first.addLocator("one.example", 10334);
    first.addLocator("two.example", 10335);
    first.setServerGroup("  orders  ");

    GeodeServerTarget.Builder second = GeodeServerTarget.builder();
    second.addLocator("two.example", 10335);
    second.addLocator("two.example", 10335);
    second.addLocator("one.example", 10334);
    second.setServerGroup("orders");

    assertThat(first.build().getAddress())
        .isEqualTo("one.example:10334,two.example:10335,two.example:10335/orders")
        .isEqualTo(second.build().getAddress());
    assertThat(first.build().getPort()).isNull();
    assertThat(second.build().getPort()).isNull();
  }

  @Test
  void locatorDiscoveryListsAreNotLimitedToFiveEndpoints() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("z.example", 10334);
    builder.addLocator("e.example", 10334);
    builder.addLocator("d.example", 10334);
    builder.addLocator("c.example", 10334);
    builder.addLocator("b.example", 10334);
    builder.addLocator("a.example", 10334);
    builder.setServerGroup("orders");

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress())
        .isEqualTo(
            "a.example:10334,b.example:10334,c.example:10334,d.example:10334,"
                + "e.example:10334,z.example:10334/orders");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void explicitServersArePreferredOverLocatorDiscovery() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("locator.example", 10334);
    builder.setServerGroup("orders");
    builder.addServer("cache.example", 40404);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void serverThatCannotBeNamedDropsTheServerList() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("one.example", 40404);
    builder.addServer("  ", 40405);

    assertThat(builder.build()).isNull();

    GeodeServerTarget.Builder outOfRange = GeodeServerTarget.builder();
    outOfRange.addServer("one.example", 0);

    assertThat(outOfRange.build()).isNull();
  }

  @Test
  void invalidServerAfterTheFirstFiveDropsTheCompleteList() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("one.example", 40404);
    builder.addServer("two.example", 40404);
    builder.addServer("three.example", 40404);
    builder.addServer("four.example", 40404);
    builder.addServer("five.example", 40404);
    builder.addServer("  ", 40404);

    assertThat(builder.build()).isNull();
  }

  @Test
  void locatorThatCannotBeNamedDropsTheLocatorList() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("one.example", 10334);
    builder.addLocator("  ", 10335);
    builder.setServerGroup("orders");

    assertThat(builder.build()).isNull();
  }

  @ParameterizedTest
  @MethodSource("safeHosts")
  void safeHostsAreAccepted(String host, String expected) {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer(host, 40404);

    assertThat(builder.build().getAddress()).isEqualTo(expected);
  }

  private static Stream<Arguments> safeHosts() {
    return Stream.of(
        argumentSet("hostname", "  cache.example  ", "cache.example"),
        argumentSet("absolute hostname", "cache.example.", "cache.example."),
        argumentSet("IPv4", "192.0.2.1", "192.0.2.1"),
        argumentSet("IPv6", "2001:db8::1", "2001:db8::1"),
        argumentSet("bracketed IPv6", "[2001:db8::1]", "2001:db8::1"),
        argumentSet("IPv4-embedded IPv6", "::ffff:192.0.2.1", "::ffff:192.0.2.1"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        " ",
        "[",
        "]",
        "[]",
        "user:secret@cache.example",
        "https://cache.example",
        "cache.example/path",
        "cache.example?token=secret",
        "cache.example#fragment",
        "cache.example:40404",
        "cache.example,other.example",
        "[2001:db8::1]:40404",
        "2001:db8::zz",
        "256.0.0.1",
        "-cache.example",
        "cache..example",
        "cache_example"
      })
  void unsafeHostDropsTheEntireEndpointList(String host) {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("safe.example", 40404);
    builder.addServer(host, 40404);

    assertThat(builder.build()).isNull();
  }

  @Test
  void safeServerGroupIsAppendedAsOnePathSegment() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("locator.example", 10334);
    builder.setServerGroup("  orders-v1_2~blue  ");

    assertThat(builder.build().getAddress()).isEqualTo("locator.example:10334/orders-v1_2~blue");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        ".",
        "..",
        "orders/eu",
        "orders\\eu",
        "orders?token=secret",
        "orders#blue",
        "orders%2Feu",
        "order group",
        "user@domain"
      })
  void unsafeServerGroupIsOmittedWithoutDroppingTheLocator(String group) {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addLocator("locator.example", 10334);
    builder.setServerGroup(group);

    GeodeServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("locator.example:10334");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void resetForgetsTheConfiguration() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("one.example", 40404);
    builder.addLocator("locator.example", 10334);
    builder.setServerGroup("orders");
    builder.reset();

    assertThat(builder.build()).isNull();

    builder.addServer("two.example", 40405);
    assertThat(builder.build().getAddress()).isEqualTo("two.example");
    assertThat(builder.build().getPort()).isEqualTo(40405);
  }

  @Test
  void builtTargetIsNotChangedByLaterConfiguration() {
    GeodeServerTarget.Builder builder = GeodeServerTarget.builder();
    builder.addServer("one.example", 40404);

    GeodeServerTarget target = builder.build();

    builder.addServer("two.example", 40405);
    builder.setServerGroup("orders");

    assertThat(target.getAddress()).isEqualTo("one.example");
    assertThat(target.getPort()).isNull();
    assertThat(builder.build().getAddress()).isEqualTo("one.example:40404,two.example:40405");
  }
}
