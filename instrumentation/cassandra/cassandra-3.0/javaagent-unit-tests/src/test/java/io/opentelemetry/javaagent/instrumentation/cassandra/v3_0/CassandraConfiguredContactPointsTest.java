/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.driver.core.EndPoint;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class CassandraConfiguredContactPointsTest {

  @Test
  void omitsImplicitDefaultPortFromSingleHostTarget() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(new String[] {"db.example"}, 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("db.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void omitsMaterializedDefaultPortFromSingleHostTarget() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(
            InetSocketAddress.createUnresolved("db.example", 9042), 9142);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("db.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void omitsDefaultPortFromMultipleContactPoints() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(
            asList("second.example", InetSocketAddress.createUnresolved("first.example", 9042)),
            9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("first.example,second.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void extractsSharedNonDefaultPortFromMultipleContactPoints() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(
            asList("second.example", InetSocketAddress.createUnresolved("first.example", 9142)),
            9142);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("first.example,second.example");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  @Test
  void inlinesMixedPortsAndCreatesStableTargetForPermutations() throws UnknownHostException {
    InetAddress address = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
    InetSocketAddress otherAddress = InetSocketAddress.createUnresolved("other.example", 9142);
    CassandraConfiguredTarget firstTarget =
        CassandraConfiguredTarget.create(asList("db.example", address, otherAddress), 9042);
    CassandraConfiguredTarget secondTarget =
        CassandraConfiguredTarget.create(asList(otherAddress, "db.example", address), 9042);

    assertThat(firstTarget).isNotNull();
    assertThat(secondTarget).isNotNull();
    assertThat(firstTarget.getAddress())
        .isEqualTo("10.0.0.1:9042,db.example:9042,other.example:9142");
    assertThat(secondTarget.getAddress()).isEqualTo(firstTarget.getAddress());
    assertThat(firstTarget.getPort()).isNull();
    assertThat(secondTarget.getPort()).isNull();
  }

  @Test
  void preservesDuplicateListContactPoints() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(
            asList("second.example", "duplicate.example", "duplicate.example"), 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("duplicate.example,duplicate.example,second.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void preservesUnresolvedSocketAddress() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(
            InetSocketAddress.createUnresolved("unresolved.example", 9142), 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("unresolved.example");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  @Test
  void formatsIpv6WhenPortsAreInlined() throws UnknownHostException {
    InetAddress address =
        InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
    InetSocketAddress otherAddress = InetSocketAddress.createUnresolved("db.example", 9142);
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(asList(address, otherAddress), 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[0:0:0:0:0:0:0:1]:9042,db.example:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void unsafeUnresolvedHostDropsEntireTarget() throws UnknownHostException {
    InetSocketAddress reachable =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9042);
    InetSocketAddress unsafe = InetSocketAddress.createUnresolved("user:password@db.example", 9042);

    assertThat(CassandraConfiguredTarget.create(asList(reachable, unsafe), 9042)).isNull();
  }

  @Test
  void unsupportedEndPointDropsEntireTarget() {
    EndPoint customEndPoint = () -> new InetSocketAddress(InetAddress.getLoopbackAddress(), 9042);

    assertThat(CassandraConfiguredTarget.create(asList("db.example", customEndPoint), 9042))
        .isNull();
  }

  @Test
  void ignoresInvalidContactPointData() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(new Object[] {null, "", new Object()}, 9042);

    assertThat(target).isNull();
    assertThat(CassandraConfiguredTarget.create("db.example", 0)).isNull();
  }
}
