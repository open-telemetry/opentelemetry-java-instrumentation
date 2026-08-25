/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpymemcachedServerTargetTest {

  @Test
  void singleNodeKeepsItsHostAndPort() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(singletonList(node("cache.example", 11211)));

    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isEqualTo(11211);
  }

  @Test
  void severalNodesHaveNoSingleTarget() {
    assertThat(
            SpymemcachedServerTarget.create(
                asList(node("one.example", 11211), node("two.example", 11212))))
        .isNull();
  }

  @Test
  void clientWithoutNodesHasNoTarget() {
    assertThat(SpymemcachedServerTarget.create(null)).isNull();
    assertThat(SpymemcachedServerTarget.create(emptyList())).isNull();
  }

  @Test
  void nodeThatCannotBeNamedDropsTheNodeList() {
    assertThat(SpymemcachedServerTarget.create(singletonList(node("  ", 11212)))).isNull();
    assertThat(SpymemcachedServerTarget.create(singletonList(node("one.example", 0)))).isNull();
  }

  @Test
  void ipv6NodesKeepTheirAddress() {
    SpymemcachedServerTarget single =
        SpymemcachedServerTarget.create(singletonList(node("2001:db8::1", 11211)));

    assertThat(single.getAddress()).isEqualTo("2001:db8::1");
    assertThat(single.getPort()).isEqualTo(11211);
  }

  @Test
  void bracketedIpv6NodesLoseTheirBrackets() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(singletonList(node("[2001:db8::1]", 11211)));

    assertThat(target.getAddress()).isEqualTo("2001:db8::1");
    assertThat(target.getPort()).isEqualTo(11211);
  }

  @Test
  void hostsAreCleaned() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(singletonList(node("  cache.example  ", 11211)));

    assertThat(target.getAddress()).isEqualTo("cache.example");
  }

  @Test
  void targetIsNotChangedByLaterEditsToTheNodeList() {
    List<InetSocketAddress> nodes = new ArrayList<>();
    nodes.add(node("one.example", 11211));

    SpymemcachedServerTarget target = SpymemcachedServerTarget.create(nodes);

    nodes.add(node("two.example", 11212));
    nodes.clear();

    assertThat(target.getAddress()).isEqualTo("one.example");
    assertThat(target.getPort()).isEqualTo(11211);
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
