/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class SpymemcachedSingletonsTest {

  @Test
  void usesMemcachedDefaultPort() {
    DbServerTarget defaultPort =
        SpymemcachedSingletons.createServerTarget(singletonList(node("cache.example", 11211)));
    DbServerTarget customPort =
        SpymemcachedSingletons.createServerTarget(singletonList(node("cache.example", 11212)));

    assertThat(defaultPort.getAddress()).isEqualTo("cache.example");
    assertThat(defaultPort.getPort()).isNull();
    assertThat(customPort.getAddress()).isEqualTo("cache.example");
    assertThat(customPort.getPort()).isEqualTo(11212);
  }

  @Test
  void clientWithoutNodesHasNoTarget() {
    assertThat(SpymemcachedSingletons.createServerTarget(null)).isNull();
    assertThat(SpymemcachedSingletons.createServerTarget(emptyList())).isNull();
  }

  private static InetSocketAddress node(String host, int port) {
    return InetSocketAddress.createUnresolved(host, port);
  }
}
