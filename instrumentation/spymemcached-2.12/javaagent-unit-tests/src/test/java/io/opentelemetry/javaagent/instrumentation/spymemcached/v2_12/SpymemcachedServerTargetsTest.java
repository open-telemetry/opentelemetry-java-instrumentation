/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class SpymemcachedServerTargetsTest {

  @Test
  void usesMemcachedDefaultPort() {
    DbServerTarget defaultPort =
        SpymemcachedServerTargets.create(singletonList(node("cache.example", 11211)));
    DbServerTarget customPort =
        SpymemcachedServerTargets.create(singletonList(node("cache.example", 11212)));

    assertThat(defaultPort.getAddress()).isEqualTo("cache.example");
    assertThat(defaultPort.getPort()).isNull();
    assertThat(customPort.getAddress()).isEqualTo("cache.example");
    assertThat(customPort.getPort()).isEqualTo(11212);
  }

  @Test
  void preservesConfiguredNodeOrder() {
    DbServerTarget target =
        SpymemcachedServerTargets.create(
            asList(node("two.example", 11212), node("one.example", 11211)));

    assertThat(target.getAddress()).isEqualTo("two.example:11212,one.example:11211");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void capsTargetAtFiveEndpoints() {
    DbServerTarget target =
        SpymemcachedServerTargets.create(
            asList(
                node("one.example", 11211),
                node("two.example", 11211),
                node("three.example", 11211),
                node("four.example", 11211),
                node("five.example", 11211),
                node("six.example", 11211)));

    assertThat(target.getAddress())
        .isEqualTo("one.example,two.example,three.example,four.example,five.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clientWithoutNodesHasNoTarget() {
    assertThat(SpymemcachedServerTargets.create(null)).isNull();
    assertThat(SpymemcachedServerTargets.create(emptyList())).isNull();
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
