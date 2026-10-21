/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.driver.core.EndPoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class CassandraServerTargetTest {

  @Test
  void omitsImplicitDefaultPortFromSingleHostTarget() {
    DbServerTarget target = CassandraServerTarget.create(new String[] {"db.example"}, 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("db.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void extractsConfiguredNonDefaultPortFromSingleHostTarget() {
    DbServerTarget target = CassandraServerTarget.create("db.example", 9142);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("db.example");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  @Test
  void omitsMaterializedDefaultPortFromSingleHostTarget() {
    DbServerTarget target =
        CassandraServerTarget.create(InetSocketAddress.createUnresolved("db.example", 9042), 9142);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("db.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void omitsDefaultPortFromMultipleContactPoints() {
    DbServerTarget target =
        CassandraServerTarget.create(
            asList("second.example", InetSocketAddress.createUnresolved("first.example", 9042)),
            9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("second.example,first.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void inlinesSharedNonDefaultPortForMultipleContactPoints() {
    DbServerTarget target =
        CassandraServerTarget.create(
            asList("second.example", InetSocketAddress.createUnresolved("first.example", 9142)),
            9142);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("second.example:9142,first.example:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void inlinesMixedPortsInConfiguredOrder() throws UnknownHostException {
    InetAddress address = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
    InetSocketAddress otherAddress = InetSocketAddress.createUnresolved("other.example", 9142);
    DbServerTarget firstTarget =
        CassandraServerTarget.create(asList("db.example", address, otherAddress), 9042);
    DbServerTarget secondTarget =
        CassandraServerTarget.create(asList(otherAddress, "db.example", address), 9042);

    assertThat(firstTarget).isNotNull();
    assertThat(secondTarget).isNotNull();
    assertThat(firstTarget.getAddress())
        .isEqualTo("db.example:9042,10.0.0.1:9042,other.example:9142");
    assertThat(secondTarget.getAddress())
        .isEqualTo("other.example:9142,db.example:9042,10.0.0.1:9042");
    assertThat(firstTarget.getPort()).isNull();
    assertThat(secondTarget.getPort()).isNull();
  }

  @Test
  void preservesDuplicateListContactPoints() {
    DbServerTarget target =
        CassandraServerTarget.create(
            asList("second.example", "duplicate.example", "duplicate.example"), 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("second.example,duplicate.example,duplicate.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void preservesUnresolvedSocketAddress() {
    DbServerTarget target =
        CassandraServerTarget.create(
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
    DbServerTarget target = CassandraServerTarget.create(asList(address, otherAddress), 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[0:0:0:0:0:0:0:1]:9042,db.example:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void limitsDefaultPortListToFirstFiveInConfiguredOrder() {
    DbServerTarget firstTarget =
        CassandraServerTarget.create(
            asList("f.example", "b.example", "e.example", "a.example", "d.example", "c.example"),
            9042);
    DbServerTarget secondTarget =
        CassandraServerTarget.create(
            asList("c.example", "d.example", "a.example", "e.example", "b.example", "f.example"),
            9042);

    assertThat(firstTarget).isNotNull();
    assertThat(secondTarget).isNotNull();
    assertThat(firstTarget.getAddress())
        .isEqualTo("f.example,b.example,e.example,a.example,d.example");
    assertThat(secondTarget.getAddress())
        .isEqualTo("c.example,d.example,a.example,e.example,b.example");
    assertThat(firstTarget.getPort()).isNull();
    assertThat(secondTarget.getPort()).isNull();
  }

  @Test
  void unsupportedEndPointDropsEntireTarget() {
    EndPoint customEndPoint = () -> new InetSocketAddress(InetAddress.getLoopbackAddress(), 9042);

    assertThat(CassandraServerTarget.create(asList("db.example", customEndPoint), 9042)).isNull();
  }

  @Test
  void nullContactPointDropsEntireTarget() {
    assertThat(CassandraServerTarget.create(asList("db.example", null), 9042)).isNull();
  }

  @Test
  void invalidContactPointAfterFirstFiveDropsEntireTarget() {
    assertThat(
            CassandraServerTarget.create(
                asList("a.example", "b.example", "c.example", "d.example", "e.example", null),
                9042))
        .isNull();
  }

  @Test
  void invalidContactPointDataDropsEntireTarget() {
    DbServerTarget target =
        CassandraServerTarget.create(new Object[] {null, "", new Object()}, 9042);

    assertThat(target).isNull();
  }

  @Test
  void invalidConfiguredPortDropsEntireTarget() {
    assertThat(CassandraServerTarget.create("db.example", 0)).isNull();
  }
}
