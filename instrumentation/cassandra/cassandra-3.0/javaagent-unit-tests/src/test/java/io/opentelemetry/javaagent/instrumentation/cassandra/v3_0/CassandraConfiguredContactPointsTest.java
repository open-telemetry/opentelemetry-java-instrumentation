/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class CassandraConfiguredContactPointsTest {

  @Test
  void createsSingleHostTargetWithoutResolvingIt() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(new String[] {"db.example"}, 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("db.example");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void createsStableMultiHostTargetForPermutations() throws UnknownHostException {
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
    assertThat(target.getAddress())
        .isEqualTo("duplicate.example:9042,duplicate.example:9042,second.example:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void preservesUnresolvedSocketAddress() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(
            InetSocketAddress.createUnresolved("unresolved.example", 9042), 9142);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("unresolved.example");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void formatsIpv6InMultiHostTarget() throws UnknownHostException {
    InetAddress address =
        InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(asList(address, "db.example"), 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[0:0:0:0:0:0:0:1]:9042,db.example:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ignoresInvalidContactPointData() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(new Object[] {null, "", new Object()}, 9042);

    assertThat(target).isNull();
    assertThat(CassandraConfiguredTarget.create("db.example", 0)).isNull();
  }
}
