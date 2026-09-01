/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.SeedNode;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CouchbaseServerTargetsTest {

  @ParameterizedTest
  @MethodSource("directSeedTargets")
  void directSeedKeepsConfiguredEndpoints(
      SeedNode seedNode, String expectedAddress, Integer expectedPort) {
    CouchbaseServerTarget target = CouchbaseServerTargets.target(singleton(seedNode));

    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isEqualTo(expectedPort);
  }

  private static Stream<Arguments> directSeedTargets() {
    return Stream.of(
        argumentSet(
            "KV port",
            SeedNode.create("node.example", Optional.of(11210), Optional.empty()),
            "node.example",
            null),
        argumentSet(
            "cluster manager port",
            SeedNode.create("node.example", Optional.empty(), Optional.of(8091)),
            "node.example",
            8091),
        argumentSet(
            "KV and cluster manager ports",
            SeedNode.create("node.example", Optional.of(11210), Optional.of(8091)),
            "node.example:11210,node.example:8091",
            null));
  }

  @Test
  void dnsSrvTargetTakesPrecedenceOverDirectSeeds() {
    Set<SeedNode> seedNodes =
        new LinkedHashSet<>(
            asList(
                SeedNode.create("two.example", Optional.empty(), Optional.empty()),
                SeedNode.create("one.example", Optional.empty(), Optional.empty())));
    CouchbaseServerTarget connectionStringTarget =
        CouchbaseConnectionStrings.target("couchbases://cluster.example");
    assertThat(connectionStringTarget).isNotNull();

    Core core = mock(Core.class);
    CouchbaseServerTargets.registerSeedNodes(seedNodes, connectionStringTarget);
    CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes);

    assertThat(CouchbaseServerTargets.get(core)).isSameAs(connectionStringTarget);
    assertThat(connectionStringTarget.getAddress()).isEqualTo("cluster.example");
    assertThat(connectionStringTarget.getPort()).isNull();
  }

  @ParameterizedTest
  @MethodSource("directSeedSets")
  void directSeedEndpointsAreDistinctAndHaveDeterministicOrder(
      SeedNode first, SeedNode second, SeedNode third) {
    CouchbaseServerTarget target =
        CouchbaseServerTargets.target(new LinkedHashSet<>(asList(first, second, third)));

    assertThat(target.getAddress())
        .isEqualTo("one.example:11210,one.example:8091,one.example:8092,two.example:11211");
    assertThat(target.getPort()).isNull();
  }

  private static Stream<Arguments> directSeedSets() {
    SeedNode one = SeedNode.create("one.example", Optional.of(11210), Optional.of(8091));
    SeedNode oneWithAnotherManagerPort =
        SeedNode.create("one.example", Optional.of(11210), Optional.of(8092));
    SeedNode two = SeedNode.create("two.example", Optional.of(11211), Optional.empty());
    return Stream.of(
        argumentSet("forward insertion", one, oneWithAnotherManagerPort, two),
        argumentSet("reverse insertion", two, oneWithAnotherManagerPort, one));
  }
}
