/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CassandraServerTargetTest {

  @Test
  void singleContactPointKeepsItsHostAndPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(singletonList("cassandra.example.com:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void singleIpv6ContactPointLosesItsBrackets() {
    CassandraServerTarget target = CassandraServerTarget.of(singletonList("[::1]:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void contactPointWithoutAPortIsIgnored() {
    CassandraServerTarget target = CassandraServerTarget.of(singletonList("cassandra.example.com"));

    assertThat(target).isNull();
  }

  @Test
  void severalContactPointsBecomeOneAddressWithoutAPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("node1.example.com:9042", "10.0.0.5:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.5:9042,node1.example.com:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void duplicateContactPointsAreOneTarget() {
    CassandraServerTarget target =
        CassandraServerTarget.of(
            asList("cassandra.example.com:9042", "cassandra.example.com:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void ipv6ContactPointsStayBracketedInAGroup() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("[::1]:9042", "2001:db8::1:9042", "10.0.0.5:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.5:9042,[2001:db8::1]:9042,[::1]:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void invalidContactPointsAreIgnored() {
    assertThat(CassandraServerTarget.of(emptyList())).isNull();
    assertThat(CassandraServerTarget.of((List<String>) null)).isNull();
    assertThat(CassandraServerTarget.of(singletonList("  "))).isNull();
    assertThat(CassandraServerTarget.of(singletonList("node.example.com:not-a-port"))).isNull();

    CassandraServerTarget target =
        CassandraServerTarget.of(
            asList("missing-port", "node.example.com:9042", "invalid:not-a-port", "[::1]:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9042,node.example.com:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionThatNamesNoContactPointHasNoTarget() {
    assertThat(CassandraServerTarget.of(emptyList(), emptySet())).isNull();
  }

  @Test
  void sessionUsesMergedProgrammaticAndConfiguredContactPoints() {
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));

    CassandraServerTarget target =
        CassandraServerTarget.of(
            singletonList("configured.example.com:9042"), programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo("configured.example.com:9042,programmatic.example.com:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionDoesNotResolveConfiguredContactPointsAgain() {
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));

    CassandraServerTarget target =
        CassandraServerTarget.of(
            singletonList("configured.invalid:9042"), programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo("configured.invalid:9042,programmatic.example.com:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void theTargetDoesNotFollowLaterChangesToTheMergedContactPoints() {
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));
    CassandraServerTarget target = CassandraServerTarget.of(emptyList(), programmaticContactPoints);

    programmaticContactPoints.clear();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("programmatic.example.com");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  @Test
  void sessionWithOnlyProgrammaticContactPointsHasATarget() {
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));

    CassandraServerTarget target = CassandraServerTarget.of(emptyList(), programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("programmatic.example.com");
    assertThat(target.getPort()).isEqualTo(9142);
  }
}
