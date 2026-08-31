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
  void singleDefaultPortIsOmitted() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(singletonList(node("192.0.2.1", 11211)));

    assertThat(target.getAddress()).isEqualTo("192.0.2.1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void defaultPortsAreOmittedFromAList() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(
            asList(node("one.example", 11211), node("two.example", 11211)));

    assertThat(target.getAddress()).isEqualTo("one.example,two.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleCustomPortIsReportedSeparately() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(singletonList(node("cache.example", 11212)));

    assertThat(target.getAddress()).isEqualTo("cache.example");
    assertThat(target.getPort()).isEqualTo(11212);
  }

  @Test
  void sharedCustomPortIsReportedSeparately() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(
            asList(node("one.example", 11212), node("two.example", 11212)));

    assertThat(target.getAddress()).isEqualTo("one.example,two.example");
    assertThat(target.getPort()).isEqualTo(11212);
  }

  @Test
  void mixedPortsStayInlineAndNodeOrderIsPreserved() {
    SpymemcachedServerTarget first =
        SpymemcachedServerTarget.create(
            asList(node("one.example", 11211), node("two.example", 11212)));
    SpymemcachedServerTarget second =
        SpymemcachedServerTarget.create(
            asList(node("two.example", 11212), node("one.example", 11211)));

    assertThat(first.getAddress()).isEqualTo("one.example:11211,two.example:11212");
    assertThat(second.getAddress()).isEqualTo("two.example:11212,one.example:11211");
    assertThat(first.getPort()).isNull();
    assertThat(second.getPort()).isNull();
  }

  @Test
  void duplicateNodesArePreserved() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(
            asList(
                node("two.example", 11212),
                node("one.example", 11211),
                node("two.example", 11212)));

    assertThat(target.getAddress())
        .isEqualTo("two.example:11212,one.example:11211,two.example:11212");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clientWithoutNodesHasNoTarget() {
    assertThat(SpymemcachedServerTarget.create(null)).isNull();
    assertThat(SpymemcachedServerTarget.create(emptyList())).isNull();
  }

  @Test
  void nodeThatCannotBeNamedDropsTheNodeList() {
    assertThat(
            SpymemcachedServerTarget.create(asList(node("one.example", 11211), node("  ", 11212))))
        .isNull();
    assertThat(SpymemcachedServerTarget.create(singletonList(node("one.example", 0)))).isNull();
    assertThat(SpymemcachedServerTarget.create(asList(node("one.example", 11211), null))).isNull();
  }

  @Test
  void ipv6NodesKeepTheirAddress() {
    SpymemcachedServerTarget single =
        SpymemcachedServerTarget.create(singletonList(node("2001:db8::1", 11211)));

    assertThat(single.getAddress()).isEqualTo("2001:db8::1");
    assertThat(single.getPort()).isNull();
  }

  @Test
  void bracketedIpv6NodesLoseTheirBrackets() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(singletonList(node("[2001:db8::1]", 11211)));

    assertThat(target.getAddress()).isEqualTo("2001:db8::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ipv6NodesWithASharedPortDoNotNeedBrackets() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(
            asList(node("2001:db8::1", 11212), node("2001:db8::2", 11212)));

    assertThat(target.getAddress()).isEqualTo("2001:db8::1,2001:db8::2");
    assertThat(target.getPort()).isEqualTo(11212);
  }

  @Test
  void ipv6NodesAreBracketedInMultiNodeTargets() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(
            asList(node("[2001:db8::1]", 11211), node("two.example", 11212)));

    assertThat(target.getAddress()).isEqualTo("[2001:db8::1]:11211,two.example:11212");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void hostsAreCleaned() {
    SpymemcachedServerTarget target =
        SpymemcachedServerTarget.create(singletonList(node("  cache.example  ", 11211)));

    assertThat(target.getAddress()).isEqualTo("cache.example");
  }

  @Test
  void renderedTargetIsNotChangedByLaterEditsToTheNodeList() {
    List<InetSocketAddress> nodes = new ArrayList<>();
    nodes.add(node("one.example", 11211));
    nodes.add(node("two.example", 11212));

    SpymemcachedServerTarget target = SpymemcachedServerTarget.create(nodes);

    nodes.clear();

    assertThat(target.getAddress()).isEqualTo("one.example:11211,two.example:11212");
    assertThat(target.getPort()).isNull();
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
