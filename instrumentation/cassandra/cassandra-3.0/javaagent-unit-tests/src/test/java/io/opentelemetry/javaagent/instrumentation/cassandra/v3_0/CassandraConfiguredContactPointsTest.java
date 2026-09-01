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
import java.util.Arrays;
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
  void limitsDefaultPortListAtEndpointBoundary() {
    String first = repeat('a', 245);
    CassandraConfiguredTarget firstTarget =
        CassandraConfiguredTarget.create(asList("cccc", "bbbb", first, "bbbb"), 9042);
    CassandraConfiguredTarget secondTarget =
        CassandraConfiguredTarget.create(asList("bbbb", first, "bbbb", "cccc"), 9042);

    String expectedAddress = first + ",bbbb,bbbb";
    assertThat(expectedAddress).hasSize(255);
    assertThat(firstTarget).isNotNull();
    assertThat(secondTarget).isNotNull();
    assertThat(firstTarget.getAddress()).isEqualTo(expectedAddress);
    assertThat(secondTarget.getAddress()).isEqualTo(expectedAddress);
    assertThat(firstTarget.getPort()).isNull();
    assertThat(secondTarget.getPort()).isNull();
  }

  @Test
  void limitsSharedNonDefaultPortListAtEndpointBoundary() {
    String first = repeat('a', 255);
    CassandraConfiguredTarget target = CassandraConfiguredTarget.create(asList("z", first), 9142);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(first);
    assertThat(target.getPort()).isEqualTo(9142);
    assertThat(CassandraConfiguredTarget.create(repeat('a', 256), 9142)).isNull();
  }

  @Test
  void limitsMixedPortListAtEndpointBoundaryWithoutSplittingIpv6() throws UnknownHostException {
    InetAddress ipv6 =
        InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
    InetSocketAddress ipv6Address = new InetSocketAddress(ipv6, 9042);
    String ipv6Token = "[0:0:0:0:0:0:0:1]:9042";
    String otherHost = repeat('a', 255 - ipv6Token.length() - 1 - ":9142".length());
    InetSocketAddress otherAddress = InetSocketAddress.createUnresolved(otherHost, 9142);
    InetSocketAddress overflow = InetSocketAddress.createUnresolved("z", 9142);
    CassandraConfiguredTarget firstTarget =
        CassandraConfiguredTarget.create(asList(overflow, otherAddress, ipv6Address), 9042);
    CassandraConfiguredTarget secondTarget =
        CassandraConfiguredTarget.create(asList(ipv6Address, overflow, otherAddress), 9042);

    String expectedAddress = ipv6Token + ',' + otherHost + ":9142";
    assertThat(expectedAddress).hasSize(255);
    assertThat(firstTarget).isNotNull();
    assertThat(secondTarget).isNotNull();
    assertThat(firstTarget.getAddress()).isEqualTo(expectedAddress);
    assertThat(secondTarget.getAddress()).isEqualTo(expectedAddress);
    assertThat(firstTarget.getPort()).isNull();
    assertThat(secondTarget.getPort()).isNull();
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
  void nullContactPointDropsEntireTarget() {
    assertThat(CassandraConfiguredTarget.create(asList("db.example", null), 9042)).isNull();
  }

  @Test
  void ignoresInvalidContactPointData() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(new Object[] {null, "", new Object()}, 9042);

    assertThat(target).isNull();
    assertThat(CassandraConfiguredTarget.create("db.example", 0)).isNull();
  }

  private static String repeat(char value, int count) {
    char[] result = new char[count];
    Arrays.fill(result, value);
    return new String(result);
  }
}
